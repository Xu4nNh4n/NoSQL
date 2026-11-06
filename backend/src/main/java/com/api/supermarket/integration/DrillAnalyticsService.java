package com.api.supermarket.integration;

import com.api.supermarket.exception.AnalyticsUnavailableException;
import com.api.supermarket.exception.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final String cassandraKeyspace;
    private final String mysqlSchema;

    public boolean isHttpAvailable() {
        try {
            return client.send(HttpRequest.newBuilder(endpoint.resolve("/"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
        catch (IOException | RuntimeException ex) { return false; }
    }

    public DrillAnalyticsService(ObjectMapper json,
            @Value("${drill.url:http://localhost:8047}") String url,
            @Value("${drill.timeout-seconds:60}") int timeoutSeconds,
            @Value("${drill.cassandra.keyspace:supermarket_analytics}") String cassandraKeyspace,
            @Value("${drill.mysql.schema:supermarket_api}") String mysqlSchema) {
        this.json = json;
        endpoint = URI.create(url.replaceAll("/+$", "") + "/query.json");
        timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.cassandraKeyspace = identifier(cassandraKeyspace, "drill.cassandra.keyspace");
        this.mysqlSchema = identifier(mysqlSchema, "drill.mysql.schema");
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
                                    THEN b.remaining_quantity ELSE 0 END) AS usable_quantity
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
                """.formatted(utcDays(from, to), ReportTime.today());
        return query(sql, 31);
    }

    public Map<String, Object> inventory(int page, int size) {
        // Match FEFO's business date on the backend, regardless of Drill's container timezone.
        String sql = """
                SELECT p.sku_code, p.name, p.reorder_point,
                       SUM(CASE WHEN b.status = 'ACTIVE'
                                     AND (b.expiry_date IS NULL OR b.expiry_date >= DATE '%s')
                                THEN b.remaining_quantity ELSE 0 END) AS usable_quantity,
                       SUM(CASE WHEN b.expiry_date < DATE '%s' THEN b.remaining_quantity ELSE 0 END) AS expired_quantity,
                       COALESCE(SUM(b.damaged_quantity), 0) AS quarantined_quantity
                FROM mysql_source.supermarket_api.products p
                LEFT JOIN mysql_source.supermarket_api.inventory_batches b ON p.product_id = b.product_id
                WHERE p.is_active = TRUE
                GROUP BY p.sku_code, p.name, p.reorder_point
                ORDER BY p.sku_code
                """.formatted(ReportTime.today(), ReportTime.today());
        return query(sql + pagination(page, size), size);
    }

    public Map<String, Object> cashFlow(LocalDate from, LocalDate to) {
        return cashFlow(from, to, OperationalAnalyticsService.Dataset.OPERATIONS);
    }

    public Map<String, Object> cashFlow(LocalDate from, LocalDate to, OperationalAnalyticsService.Dataset dataset) {
        validateRange(from, to);
        EventSource source = eventSource(from, to, dataset);
        Map<String, Object> result = query("""
                SELECT CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE) AS event_date,
                       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS collected_amount,
                       SUM(CASE WHEN e.event_type = 'REFUND_CONFIRMED' THEN CAST(e.amount AS DECIMAL(18, 2)) ELSE 0 END) AS refunded_amount,
                       SUM(CASE WHEN e.event_type = 'PAYMENT_RECEIVED' THEN CAST(e.amount AS DECIMAL(18, 2))
                                WHEN e.event_type = 'REFUND_CONFIRMED' THEN -CAST(e.amount AS DECIMAL(18, 2))
                                ELSE 0 END) AS net_cash_amount
                 FROM (%s) e
                 JOIN mysql_source.__MYSQL_DB__.sales_orders o
                   ON CAST(e.order_id AS BIGINT) = o.order_id AND o.order_type = 'IN_STORE'
                 WHERE e.event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED') AND e.event_time >= TIMESTAMP '__START__' AND e.event_time < TIMESTAMP '__END__'
                GROUP BY CAST(TIMESTAMPADD(HOUR, 7, e.event_time) AS DATE)
                ORDER BY event_date
                """.formatted(source.sql()).replace("__START__", ReportTime.utcStart(from)).replace("__END__", ReportTime.utcStart(to.plusDays(1))).replace("__MYSQL_DB__", "supermarket_api"), 31);
        result.put("dataSources", source.partitions());
        result.put("missingExportDates", source.missingDates());
        result.put("dataset", dataset.name());
        result.put("sourceNames", List.of("MySQL: " + mysqlSchema, "Cassandra: " + cassandraKeyspace + ".business_events_by_day (direct)"));
        return result;
    }

    public Map<String, Object> reconciliation(LocalDate from, LocalDate to) {
        return reconciliation(from, to, OperationalAnalyticsService.Dataset.OPERATIONS);
    }

    public Map<String, Object> reconciliation(LocalDate from, LocalDate to, OperationalAnalyticsService.Dataset dataset) {
        validateRange(from, to);
        EventSource source = eventSource(from, to, dataset);
        // A successful scan is not evidence that an empty day has complete event coverage.
        String available = "COALESCE(a.astra_payment_count, 0) + COALESCE(a.astra_refund_count, 0) > 0";

        String sql = """
                WITH report_days AS (%s), astra_raw AS (
                    SELECT CAST(TIMESTAMPADD(HOUR, 7, event_time) AS DATE) AS event_date, event_type, amount, order_id
                    FROM (%s) raw WHERE event_time >= TIMESTAMP '__START__' AND event_time < TIMESTAMP '__END__'
                ),
                astra_daily AS (
                    SELECT a.event_date,
                           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED' THEN 1 ELSE 0 END) AS astra_payment_count,
                           SUM(CASE WHEN event_type = 'PAYMENT_RECEIVED'
                                    THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_collected_amount,
                           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED' THEN 1 ELSE 0 END) AS astra_refund_count,
                           SUM(CASE WHEN event_type = 'REFUND_CONFIRMED'
                                    THEN CAST(amount AS DECIMAL(18, 2)) ELSE 0 END) AS astra_refunded_amount
                    FROM astra_raw a
                    JOIN mysql_source.__MYSQL_DB__.sales_orders source_order
                      ON CAST(a.order_id AS BIGINT) = source_order.order_id
                     AND source_order.order_type = 'IN_STORE'
                    WHERE event_type IN ('PAYMENT_RECEIVED', 'REFUND_CONFIRMED')
                    GROUP BY a.event_date
                ),
                mysql_payments AS (
                    SELECT d.event_date,
                           SUM(CASE WHEN event_type = 'PAYMENT' THEN 1 ELSE 0 END) AS mysql_payment_count,
                           SUM(CASE WHEN event_type = 'PAYMENT' THEN amount ELSE 0 END) AS mysql_collected_amount,
                           SUM(CASE WHEN event_type = 'REFUND' THEN 1 ELSE 0 END) AS mysql_refund_count,
                           SUM(CASE WHEN event_type = 'REFUND' THEN amount ELSE 0 END) AS mysql_refunded_amount
                    FROM mysql_source.supermarket_api.payment_records p
                    JOIN mysql_source.supermarket_api.sales_orders o ON o.order_id = p.order_id
                    JOIN report_days d ON p.created_at >= d.utc_start AND p.created_at < d.utc_end
                    WHERE o.order_type = 'IN_STORE'
                    GROUP BY d.event_date
                ),
                mysql_orders AS (
                    SELECT d.event_date,
                           COUNT(*) AS mysql_order_count,
                           SUM(COALESCE(subtotal, 0)) AS mysql_order_subtotal,
                           SUM(COALESCE(total_amount, 0)) AS mysql_order_total
                    FROM mysql_source.supermarket_api.sales_orders o
                    JOIN report_days d ON o.order_date >= d.utc_start AND o.order_date < d.utc_end
                    WHERE o.order_type = 'IN_STORE'
                    GROUP BY d.event_date
                ),
                item_totals AS (
                    SELECT i.order_id, SUM(i.unit_price * i.quantity) AS gross_subtotal,
                           SUM(i.subtotal) AS net_subtotal,
                           COUNT(*) AS line_count, COUNT(i.net_unit_price) AS net_line_count
                    FROM mysql_source.supermarket_api.sales_order_items i
                    GROUP BY i.order_id
                ),
                mysql_items AS (
                    SELECT d.event_date,
                           SUM(COALESCE(i.net_subtotal, 0)) AS mysql_item_subtotal,
                           SUM(COALESCE(i.gross_subtotal, 0)) AS mysql_item_gross_subtotal,
                           SUM(ABS(COALESCE(o.subtotal, 0) - COALESCE(i.gross_subtotal, 0))) AS gross_mismatch_amount,
                           SUM(CASE WHEN i.line_count > 0 AND i.line_count = i.net_line_count THEN 1 ELSE 0 END) AS net_checked_order_count,
                           SUM(CASE WHEN i.line_count > 0 AND i.line_count = i.net_line_count
                                    THEN o.total_amount - i.net_subtotal ELSE 0 END) AS net_subtotal_delta,
                           SUM(CASE WHEN i.line_count > 0 AND i.line_count = i.net_line_count
                                    THEN ABS(o.total_amount - i.net_subtotal) ELSE 0 END) AS net_mismatch_amount
                    FROM mysql_source.supermarket_api.sales_orders o
                    LEFT JOIN item_totals i ON i.order_id = o.order_id
                    JOIN report_days d ON o.order_date >= d.utc_start AND o.order_date < d.utc_end
                    WHERE o.order_type = 'IN_STORE'
                    GROUP BY d.event_date
                ),
                days AS (
                    SELECT event_date FROM report_days
                )
                SELECT d.event_date,
                       %s AS export_available,
                       COALESCE(o.mysql_order_count, 0) AS mysql_order_count,
                       COALESCE(o.mysql_order_subtotal, 0) AS mysql_order_subtotal,
                       COALESCE(i.mysql_item_subtotal, 0) AS mysql_item_subtotal,
                       COALESCE(i.mysql_item_gross_subtotal, 0) AS mysql_item_gross_subtotal,
                       COALESCE(o.mysql_order_subtotal, 0) - COALESCE(i.mysql_item_gross_subtotal, 0)
                           AS item_subtotal_delta,
                       COALESCE(i.net_checked_order_count, 0) AS net_checked_order_count,
                       COALESCE(o.mysql_order_count, 0) - COALESCE(i.net_checked_order_count, 0) AS net_skipped_order_count,
                       COALESCE(i.net_subtotal_delta, 0) AS net_subtotal_delta,
                       COALESCE(i.gross_mismatch_amount, 0) AS gross_mismatch_amount,
                       COALESCE(i.net_mismatch_amount, 0) AS net_mismatch_amount,
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
                       CASE WHEN (%s) AND COALESCE(i.gross_mismatch_amount, 0) <= 0.01
                                  AND COALESCE(i.net_mismatch_amount, 0) <= 0.01
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
                """.formatted(utcDays(from, to), source.sql(), available, available)
                .replace("__START__", ReportTime.utcStart(from)).replace("__END__", ReportTime.utcStart(to.plusDays(1)))
                .replace("__MYSQL_DB__", "supermarket_api")
                .replace("o.order_date", "o.created_at");
        Map<String, Object> result = query(sql, 31);
        result.put("dataSources", source.partitions());
        result.put("missingExportDates", source.missingDates());
        result.put("dataset", dataset.name());
        result.put("sourceNames", List.of("MySQL: " + mysqlSchema,
                "Cassandra: " + cassandraKeyspace + ".business_events_by_day (direct)"));
        result.put("availabilityMeaning", "export_available is a legacy field: true means payment/refund events were observed; completeness is not verified");
        result.put("netCheckMeaning", "Net totals are checked only for orders whose every line has net_unit_price; legacy orders are skipped");
        return result;
    }

    private record EventSource(String sql, List<Map<String, String>> partitions, List<String> missingDates) { }

    private String utcDays(LocalDate from, LocalDate to) {
        List<String> days = new ArrayList<>();
        // MySQL TIMESTAMP is read as an instant by Drill's JDBC writer.
        // configure-storage.ps1 forces the JDBC session to UTC, matching Astra dates.
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add("(DATE '" + date + "', TIMESTAMP '" + ReportTime.utcStart(date)
                    + "', TIMESTAMP '" + ReportTime.utcStart(date.plusDays(1)) + "')");
        }
        // A single VALUES relation avoids expanding UNION branches each time this CTE is joined.
        return "SELECT * FROM (VALUES " + String.join(", ", days) + ") AS d(event_date, utc_start, utc_end)";
    }

    private EventSource eventSource(LocalDate from, LocalDate to, OperationalAnalyticsService.Dataset dataset) {
        if (dataset != OperationalAnalyticsService.Dataset.OPERATIONS) {
            throw new BadRequestException("Direct Cassandra reports support OPERATIONS only; SIMULATED is not configured");
        }
        String reference = "cassandra_source.`" + cassandraKeyspace + "`.business_events_by_day";
        LocalDate firstUtcDay = from.atStartOfDay(ReportTime.ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        // Calcite's Cassandra reader emits DATE as epoch-day INT and TIMESTAMP as epoch-ms BIGINT.
        // Range predicates stay in Drill, so compare epoch days explicitly rather than DATE milliseconds.
        // shortcut: this scans Cassandra without partition pruning; revisit for large event histories.
        String sql = """
                SELECT CAST(TO_TIMESTAMP(CAST(event_date AS BIGINT) * 86400) AS DATE) AS event_date,
                       CAST(event_type AS VARCHAR) AS event_type,
                       CAST(amount AS DECIMAL(18, 2)) AS amount,
                       CAST(order_id AS BIGINT) AS order_id,
                       TO_TIMESTAMP(CAST(event_time AS DOUBLE) / 1000.0) AS event_time
                FROM %s
                WHERE CAST(event_date AS BIGINT) >= %d AND CAST(event_date AS BIGINT) <= %d
                """.formatted(reference, firstUtcDay.toEpochDay(), to.toEpochDay());
        return new EventSource(sql, List.of(Map.of("format", "CASSANDRA", "reference", reference,
                "fromPartition", firstUtcDay.toString(), "toPartition", to.toString())), List.of());
    }

    static String identifier(String value, String property) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,47}")) {
            throw new IllegalStateException(property + " must be a simple SQL identifier (maximum 48 characters)");
        }
        return value;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30)
            throw new BadRequestException("Khoảng ngày Việt Nam phải hợp lệ và tối đa 31 ngày");
    }

    private String pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 500) throw new BadRequestException("page >= 0, size từ 1 đến 500");
        return " LIMIT " + size + " OFFSET " + ((long) page * size);
    }

    Map<String, Object> query(String sql, int limit) {
        sql = sql.replace("mysql_source.supermarket_api.", "mysql_source.`" + mysqlSchema + "`.");
        // Only fixed SELECT reports are exposed; callers cannot submit arbitrary SQL.
        try {
            long started = System.nanoTime();
            String body = json.writeValueAsString(Map.of("queryType", "SQL", "query", sql, "autoLimit", limit));
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                try { throw drillFailure(json.readTree(response.body())); }
                catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                    throw new AnalyticsUnavailableException("Drill trả HTTP " + response.statusCode() + "; kiểm tra container và giao diện Drill");
                }
            }
            JsonNode result = json.readTree(response.body());
            String state = result == null ? "" : result.path("queryState").asText();
            if (result == null || (!state.isEmpty() && !"COMPLETED".equals(state))
                    || !result.path("rows").isArray() || result.hasNonNull("errorMessage")) {
                log.warn("Drill report chưa thành công: state={}, queryId={}", state,
                        result == null ? "" : result.path("queryId").asText());
                throw drillFailure(result);
            }
            // Return plain values so the response also works with Spring Boot 4's Jackson 3 converter.
            Map<String, Object> report = json.convertValue(result, new TypeReference<Map<String, Object>>() { });
            // Fixed SELECT SQL only; expose the actual query for explaining Drill, never connection credentials.
            report.put("sql", sql);
            report.put("timeZone", "Asia/Ho_Chi_Minh");
            report.put("generatedAt", Instant.now().toString());
            report.put("profilesUrl", endpoint.resolve("/profiles").toString());
            report.put("requestElapsedMs", (System.nanoTime() - started) / 1_000_000);
            return report;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AnalyticsUnavailableException("Yêu cầu báo cáo bị gián đoạn");
        } catch (HttpTimeoutException ex) {
            throw new AnalyticsUnavailableException("Drill vượt thời gian chờ báo cáo; thu hẹp khoảng ngày hoặc số dòng kết quả");
        } catch (IOException ex) {
            log.warn("Không lấy được báo cáo Drill, loại lỗi={}", ex.getClass().getSimpleName());
            throw new AnalyticsUnavailableException("Không kết nối được Drill hoặc đã hết thời gian chờ");
        }
    }

    private AnalyticsUnavailableException drillFailure(JsonNode result) {
        String detail = result == null ? "" : result.path("errorMessage").asText().toLowerCase(java.util.Locale.ROOT);
        String message;
        if (detail.contains("driver class")) message = "Drill chưa nạp được JDBC driver MySQL; cần build lại image Drill";
        else if (detail.contains("outofmemory") || detail.contains("memory limit") || detail.contains("unable to allocate"))
            message = "Drill thiếu bộ nhớ cho truy vấn; thu hẹp khoảng ngày hoặc Top SKU";
        else if (detail.contains("not found") || detail.contains("unknown database") || detail.contains("does not exist"))
            message = "Drill chưa tìm thấy bảng hoặc file của nguồn đã chọn; kiểm tra schema/keyspace đã cấu hình và dữ liệu đã nạp";
        else if (detail.contains("connection") || detail.contains("timed out"))
            message = "Drill không kết nối được một nguồn dữ liệu; kiểm tra dịch vụ local và storage plugin";
        else message = "Drill không xử lý được SQL báo cáo; mở Profiles để xem nguyên nhân";
        String id = result == null ? "" : result.path("queryId").asText();
        if (id.matches("[a-zA-Z0-9-]{1,80}")) message += ". Mã truy vấn Drill: " + id;
        return new AnalyticsUnavailableException(message);
    }
}
