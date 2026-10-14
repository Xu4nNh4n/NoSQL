package com.api.supermarket.integration;

import com.api.supermarket.exception.AnalyticsUnavailableException;
import com.api.supermarket.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DrillAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(DrillAnalyticsService.class);
    private final ObjectMapper json;
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final Path exports;
    private final Path curated;

    public DrillAnalyticsService(ObjectMapper json,
            @Value("${drill.url:http://localhost:8047}") String url,
            @Value("${drill.timeout-seconds:60}") int timeoutSeconds,
            @Value("${integration.analytics-directory:analytics/cassandra}") String directory) {
        this.json = json;
        endpoint = URI.create(url.replaceAll("/+$", "") + "/query.json");
        timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        exports = Path.of(directory).toAbsolutePath().normalize();
        curated = exports.resolveSibling("curated");
    }

    public Map<String, Object> dashboard(LocalDate from, LocalDate to) {
        validateRange(from, to);
        String sql = """
                WITH days AS (%s), payments AS (
                    SELECT d.event_date,
                           SUM(CASE WHEN event_type = 'PAYMENT' THEN amount ELSE 0 END) AS collected_amount,
                           SUM(CASE WHEN event_type = 'REFUND' THEN amount ELSE 0 END) AS refunded_amount,
                           SUM(CASE WHEN event_type = 'PAYMENT' THEN 1 ELSE 0 END) AS payment_count
                    FROM mysql_source.supermarket_api.payment_records p
                    JOIN days d ON p.created_at >= d.utc_start AND p.created_at < d.utc_end
                    GROUP BY d.event_date
                ), stock AS (
                    SELECT p.product_id, p.reorder_point,
                           SUM(CASE WHEN b.status = 'ACTIVE'
                                     AND (b.expiry_date IS NULL OR b.expiry_date >= DATE '%s')
                                    THEN b.remaining_quantity - b.reserved_quantity ELSE 0 END) AS usable_quantity
                    FROM mysql_source.supermarket_api.products p
                    LEFT JOIN mysql_source.supermarket_api.inventory_batches b ON b.product_id = p.product_id
                    WHERE p.is_active = TRUE
                    GROUP BY p.product_id, p.reorder_point
                ), stock_summary AS (
                    SELECT COALESCE(SUM(CASE WHEN usable_quantity <= reorder_point THEN 1 ELSE 0 END), 0) AS low_stock_count,
                           COALESCE(SUM(CASE WHEN usable_quantity <= 0 THEN 1 ELSE 0 END), 0) AS out_of_stock_count
                    FROM stock
                )
                SELECT d.event_date,
                       COALESCE(p.collected_amount, 0) AS collected_amount,
                       COALESCE(p.refunded_amount, 0) AS refunded_amount,
                       COALESCE(p.collected_amount, 0) - COALESCE(p.refunded_amount, 0) AS net_cash_amount,
                       COALESCE(p.payment_count, 0) AS payment_count,
                       s.low_stock_count, s.out_of_stock_count
                FROM days d
                LEFT JOIN payments p ON p.event_date = d.event_date
                CROSS JOIN stock_summary s
                ORDER BY d.event_date
                """.formatted(utcDays(from, to), LocalDate.now());
        return query(sql, 31);
    }

    public Map<String, Object> inventory(int page, int size) {
        // Match FEFO's business date on the backend, regardless of Drill's container timezone.
        String sql = """
                SELECT p.sku_code, p.name, p.reorder_point,
                       SUM(CASE WHEN b.status = 'ACTIVE'
                                     AND (b.expiry_date IS NULL OR b.expiry_date >= DATE '%s')
                                THEN b.remaining_quantity - b.reserved_quantity ELSE 0 END) AS usable_quantity,
                       SUM(CASE WHEN b.expiry_date < DATE '%s' THEN b.remaining_quantity ELSE 0 END) AS expired_quantity,
                       COALESCE(SUM(b.damaged_quantity), 0) AS quarantined_quantity
                FROM mysql_source.supermarket_api.products p
                LEFT JOIN mysql_source.supermarket_api.inventory_batches b ON p.product_id = b.product_id
                WHERE p.is_active = TRUE
                GROUP BY p.sku_code, p.name, p.reorder_point
                ORDER BY p.sku_code
                """.formatted(LocalDate.now(), LocalDate.now());
        return query(sql + pagination(page, size), size);
    }

    public Map<String, Object> cashFlow(LocalDate from, LocalDate to) {
        validateRange(from, to);
        EventSource source = eventSource(from, to);
        Map<String, Object> result = query("""
                SELECT CAST(e.event_date AS DATE) AS event_date,
                       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS collected_amount,
                       SUM(CASE WHEN e.event_type = 'REFUND_CONFIRMED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS refunded_amount,
                       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2))
                                WHEN e.event_type = 'REFUND_CONFIRMED' THEN -CAST(e.amount AS DECIMAL(18, 2))
                                ELSE 0 END) AS net_cash_amount
                FROM (%s) e
                GROUP BY CAST(e.event_date AS DATE)
                ORDER BY event_date
                """.formatted(source.sql()), 31);
        result.put("dataSources", source.partitions());
        result.put("missingExportDates", source.missingDates());
        return result;
    }

    public Map<String, Object> reconciliation(LocalDate from, LocalDate to) {
        validateRange(from, to);
        EventSource source = eventSource(from, to);

        String sql = """
                WITH report_days AS (%s), astra_raw AS (
                    SELECT CAST(event_date AS DATE) AS event_date, event_type, amount
                    FROM (%s) raw
                ),
                astra_daily AS (
                    SELECT event_date,
                           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED' THEN 1 ELSE 0 END) AS astra_payment_count,
                           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED'
                                    THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_collected_amount,
                           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED' THEN 1 ELSE 0 END) AS astra_refund_count,
                           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED'
                                    THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_refunded_amount
                    FROM astra_raw
                    WHERE event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED')
                    GROUP BY event_date
                ),
                mysql_payments AS (
                    SELECT d.event_date,
                           SUM(CASE WHEN event_type = 'PAYMENT' THEN 1 ELSE 0 END) AS mysql_payment_count,
                           SUM(CASE WHEN event_type = 'PAYMENT' THEN amount ELSE 0 END) AS mysql_collected_amount,
                           SUM(CASE WHEN event_type = 'REFUND' THEN 1 ELSE 0 END) AS mysql_refund_count,
                           SUM(CASE WHEN event_type = 'REFUND' THEN amount ELSE 0 END) AS mysql_refunded_amount
                    FROM mysql_source.supermarket_api.payment_records p
                    JOIN report_days d ON p.created_at >= d.utc_start AND p.created_at < d.utc_end
                    GROUP BY d.event_date
                ),
                mysql_orders AS (
                    SELECT d.event_date,
                           COUNT(*) AS mysql_order_count,
                           SUM(COALESCE(subtotal, 0)) AS mysql_order_subtotal,
                           SUM(COALESCE(total_amount, 0)) AS mysql_order_total
                    FROM mysql_source.supermarket_api.sales_orders o
                    JOIN report_days d ON o.order_date >= d.utc_start AND o.order_date < d.utc_end
                    GROUP BY d.event_date
                ),
                mysql_items AS (
                    SELECT d.event_date,
                           SUM(COALESCE(i.subtotal, 0)) AS mysql_item_subtotal
                    FROM mysql_source.supermarket_api.sales_orders o
                    JOIN mysql_source.supermarket_api.sales_order_items i ON i.order_id = o.order_id
                    JOIN report_days d ON o.order_date >= d.utc_start AND o.order_date < d.utc_end
                    GROUP BY d.event_date
                ),
                days AS (
                    SELECT event_date FROM astra_daily
                    UNION
                    SELECT event_date FROM mysql_payments
                    UNION
                    SELECT event_date FROM mysql_orders
                )
                SELECT d.event_date,
                       COALESCE(o.mysql_order_count, 0) AS mysql_order_count,
                       COALESCE(o.mysql_order_subtotal, 0) AS mysql_order_subtotal,
                       COALESCE(i.mysql_item_subtotal, 0) AS mysql_item_subtotal,
                       COALESCE(o.mysql_order_subtotal, 0) - COALESCE(i.mysql_item_subtotal, 0)
                           AS item_subtotal_delta,
                       COALESCE(p.mysql_payment_count, 0) AS mysql_payment_count,
                       COALESCE(a.astra_payment_count, 0) AS astra_payment_count,
                       COALESCE(p.mysql_collected_amount, 0) AS mysql_collected_amount,
                       COALESCE(a.astra_collected_amount, 0) AS astra_collected_amount,
                       COALESCE(p.mysql_collected_amount, 0) - COALESCE(a.astra_collected_amount, 0)
                           AS collected_delta,
                       COALESCE(p.mysql_refund_count, 0) AS mysql_refund_count,
                       COALESCE(a.astra_refund_count, 0) AS astra_refund_count,
                       COALESCE(p.mysql_refunded_amount, 0) AS mysql_refunded_amount,
                       COALESCE(a.astra_refunded_amount, 0) AS astra_refunded_amount,
                       COALESCE(p.mysql_refunded_amount, 0) - COALESCE(a.astra_refunded_amount, 0)
                           AS refunded_delta,
                       CASE WHEN ABS(COALESCE(o.mysql_order_subtotal, 0) - COALESCE(i.mysql_item_subtotal, 0)) <= 0.01
                                  AND COALESCE(p.mysql_payment_count, 0) = COALESCE(a.astra_payment_count, 0)
                                  AND ABS(COALESCE(p.mysql_collected_amount, 0) - COALESCE(a.astra_collected_amount, 0)) <= 0.01
                                  AND COALESCE(p.mysql_refund_count, 0) = COALESCE(a.astra_refund_count, 0)
                                  AND ABS(COALESCE(p.mysql_refunded_amount, 0) - COALESCE(a.astra_refunded_amount, 0)) <= 0.01
                            THEN 'PASS' ELSE 'CHECK' END AS quality_status
                FROM days d
                LEFT JOIN mysql_orders o ON o.event_date = d.event_date
                LEFT JOIN mysql_items i ON i.event_date = d.event_date
                LEFT JOIN mysql_payments p ON p.event_date = d.event_date
                LEFT JOIN astra_daily a ON a.event_date = d.event_date
                ORDER BY d.event_date
                """.formatted(utcDays(from, to), source.sql());
        Map<String, Object> result = query(sql, 31);
        result.put("dataSources", source.partitions());
        result.put("missingExportDates", source.missingDates());
        return result;
    }

    private record EventSource(String sql, List<Map<String, String>> partitions, List<String> missingDates) { }

    private String utcDays(LocalDate from, LocalDate to) {
        List<String> days = new ArrayList<>();
        // MySQL TIMESTAMP is read as an instant by Drill's JDBC writer.
        // configure-storage.ps1 forces the JDBC session to UTC, matching Astra dates.
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add("SELECT DATE '" + date + "' AS event_date, TIMESTAMP '" + date
                    + " 00:00:00' AS utc_start, TIMESTAMP '" + date.plusDays(1) + " 00:00:00' AS utc_end");
        }
        return String.join(" UNION ALL ", days);
    }

    private EventSource eventSource(LocalDate from, LocalDate to) {
        List<String> queries = new ArrayList<>();
        List<Map<String, String>> partitions = new ArrayList<>();
        List<String> missingDates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Path source = exports.resolve("events-" + date + ".json");
            if (!Files.isRegularFile(source)) {
                missingDates.add(date.toString());
                continue;
            }
            String table = currentParquetTable(date, source);
            if (table != null) {
                queries.add("SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount FROM analytics.curated.`" + table + "`");
            } else {
                queries.add("""
                        SELECT CAST(event_date AS DATE) AS event_date, event_type, CAST(amount AS DECIMAL(18, 2)) AS amount FROM (
                            SELECT event_date, event_type, amount, event_id,
                                   ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
                            FROM analytics.cassandra.`events-%s.json`
                        ) raw
                        WHERE event_rank = 1 AND event_type IS NOT NULL AND event_id IS NOT NULL
                          AND event_id <> '11111111-1111-1111-1111-111111111111'
                        """.formatted(date));
            }
            String exportedAt;
            try {
                exportedAt = Files.getLastModifiedTime(source).toInstant().toString();
            } catch (IOException ex) {
                exportedAt = "";
            }
            partitions.add(Map.of("date", date.toString(), "format", table == null ? "JSON" : "PARQUET",
                    "exportedAt", exportedAt, "reference", table == null
                            ? "analytics.cassandra.events-" + date + ".json" : "analytics.curated." + table));
        }
        if (queries.isEmpty()) throw new BadRequestException("Chưa có bản xuất Astra cho khoảng ngày này");
        return new EventSource(String.join(" UNION ALL ", queries), partitions, missingDates);
    }

    private String currentParquetTable(LocalDate date, Path source) {
        String prefix = "events_" + date.toString().replace('-', '_');
        Path manifest = curated.resolve(prefix + ".ready.json");
        if (!Files.isRegularFile(manifest)) return null;
        try {
            JsonNode metadata = json.readTree(Files.readString(manifest));
            String table = metadata.path("table").asText();
            if (!table.matches(prefix + "_[a-f0-9]{32}")) return null;
            Path tablePath = curated.resolve(table);
            if (!Files.isDirectory(tablePath)) return null;
            try (var files = Files.list(tablePath)) {
                if (files.noneMatch(file -> Files.isRegularFile(file) && file.toString().endsWith(".parquet"))) return null;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(source), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(metadata.path("sourceSha256").asText())
                    ? table : null;
        } catch (IOException | RuntimeException ex) {
            log.warn("Parquet ngày {} chưa sẵn sàng; dùng JSON, loại lỗi={}", date, ex.getClass().getSimpleName());
            return null;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không được hỗ trợ", ex);
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30)
            throw new BadRequestException("Khoảng ngày UTC phải hợp lệ và tối đa 31 ngày");
    }

    private String pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 500) throw new BadRequestException("page >= 0, size từ 1 đến 500");
        return " LIMIT " + size + " OFFSET " + ((long) page * size);
    }

    Map<String, Object> query(String sql, int limit) {
        // Only fixed SELECT reports are exposed; callers cannot submit arbitrary SQL.
        try {
            long started = System.nanoTime();
            String body = json.writeValueAsString(Map.of("queryType", "SQL", "query", sql, "autoLimit", limit));
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new AnalyticsUnavailableException("Drill chưa trả được báo cáo; kiểm tra container và storage");
            JsonNode result = json.readTree(response.body());
            String state = result == null ? "" : result.path("queryState").asText();
            if (result == null || (!state.isEmpty() && !"COMPLETED".equals(state))
                    || !result.path("rows").isArray() || result.hasNonNull("errorMessage")) {
                log.warn("Drill report chưa thành công: state={}, queryId={}", state,
                        result == null ? "" : result.path("queryId").asText());
                throw new AnalyticsUnavailableException("Drill không thực hiện được báo cáo; xem Profiles trên giao diện Drill");
            }
            // Return plain values so the response also works with Spring Boot 4's Jackson 3 converter.
            Map<String, Object> report = json.convertValue(result, new TypeReference<Map<String, Object>>() { });
            // Fixed SELECT SQL only; expose the actual query for explaining Drill, never connection credentials.
            report.put("sql", sql);
            report.put("generatedAt", Instant.now().toString());
            report.put("profilesUrl", endpoint.resolve("/profiles").toString());
            report.put("requestElapsedMs", (System.nanoTime() - started) / 1_000_000);
            return report;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AnalyticsUnavailableException("Yêu cầu báo cáo bị gián đoạn");
        } catch (IOException ex) {
            log.warn("Không lấy được báo cáo Drill, loại lỗi={}", ex.getClass().getSimpleName());
            throw new AnalyticsUnavailableException("Không kết nối được Drill hoặc đã hết thời gian chờ");
        }
    }
}
