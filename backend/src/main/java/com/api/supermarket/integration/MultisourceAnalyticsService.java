package com.api.supermarket.integration;

import com.api.supermarket.exception.BadRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MultisourceAnalyticsService {
    private final DrillAnalyticsService drill;
    private final String mysql;
    private final String stockTable;
    private final Path paymentLogs;

    public MultisourceAnalyticsService(DrillAnalyticsService drill,
            @Value("${drill.mysql.schema:supermarket_api}") String schema,
            @Value("${drill.cassandra.keyspace:supermarket_analytics}") String keyspace,
            @Value("${integration.pos-payment-log-directory:../analytics/pos-payments}") String paymentDirectory) {
        this.drill = drill;
        mysql = "mysql_source.`" + DrillAnalyticsService.identifier(schema, "drill.mysql.schema") + "`.";
        stockTable = "cassandra_source.`" + DrillAnalyticsService.identifier(keyspace, "drill.cassandra.keyspace") + "`.stock_daily_by_day";
        paymentLogs = Path.of(paymentDirectory).toAbsolutePath().normalize();
    }

    public Map<String, Object> purchaseCosts(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365) {
            throw new BadRequestException("Chọn khoảng ngày nhập hợp lệ, tối đa 366 ngày");
        }
        String sql = """
                WITH history AS (
                    SELECT b.*, LAG(b.unit_cost) OVER (
                        PARTITION BY b.product_id ORDER BY b.received_at, b.batch_id
                    ) AS previous_unit_cost
                    FROM %sinventory_batches b
                )
                SELECT b.batch_id, b.batch_code, b.product_id, p.sku_code AS sku,
                       p.name AS product_name, COALESCE(b.supplier_name, s.name) AS supplier_name,
                       b.received_at AS received_at,
                       CAST(b.unit_cost AS VARCHAR) AS unit_cost,
                       CAST(b.previous_unit_cost AS VARCHAR) AS previous_unit_cost,
                       CAST(b.unit_cost - b.previous_unit_cost AS VARCHAR) AS price_change,
                       b.received_quantity, b.remaining_quantity,
                       CAST(CAST(b.unit_cost * CAST(b.received_quantity AS BIGINT) AS DECIMAL(30,2)) AS VARCHAR) AS received_value,
                       CAST(CAST(b.unit_cost * CAST(b.remaining_quantity AS BIGINT) AS DECIMAL(30,2)) AS VARCHAR) AS stock_value
                FROM history b
                JOIN %sproducts p ON p.product_id = b.product_id
                LEFT JOIN %ssuppliers s ON s.supplier_id = b.supplier_id
                WHERE b.received_at >= TIMESTAMP '%s' AND b.received_at < TIMESTAMP '%s'
                ORDER BY b.received_at DESC, b.batch_id DESC
                """.formatted(mysql, mysql, mysql, ReportTime.utcStart(from), ReportTime.utcStart(to.plusDays(1)));
        Map<String, Object> result = drill.query(sql, 10000);
        result.put("sourceNames", List.of("MySQL: inventory_batches, products, suppliers"));
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("rowLimit", 10000);
        result.put("timezone", ReportTime.ZONE.getId());
        result.put("definition", "Giá nhập thực tế theo lô; so với lô nhập liền trước của cùng sản phẩm, kể cả lô ngoài khoảng lọc. Không phải báo giá tương lai hoặc lợi nhuận.");
        result.put("stockMeaning", "Giá trị tồn = lượng còn lại hiện tại nhân giá nhập lô, gồm cả lô hết hạn hoặc ngừng bán; không phải tồn tại ngày kết thúc bộ lọc.");
        return result;
    }

    public Map<String, Object> stockHistory(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30) {
            throw new BadRequestException("from and to are required; range must contain 1 to 31 Vietnam dates");
        }
        // snapshot_date is a Vietnam business date emitted as epoch-day INT by the Cassandra reader.
        // shortcut: numeric range filtering stays in Drill and scans Cassandra without partition pruning.
        String predicate = "CAST(snapshot_date AS BIGINT) >= " + from.toEpochDay()
                + " AND CAST(snapshot_date AS BIGINT) <= " + to.toEpochDay();
        String sql = """
                SELECT CAST(TO_TIMESTAMP(CAST(s.snapshot_date AS BIGINT) * 86400) AS DATE) AS snapshot_date,
                       CAST(s.product_id AS BIGINT) AS product_id,
                       p.sku_code AS sku, p.name AS product_name,
                       CAST(s.remaining_quantity AS BIGINT) AS remaining_quantity,
                       CAST(s.opening AS BIGINT) AS opening_quantity,
                       CAST(s.received AS BIGINT) AS received_quantity,
                       CAST(s.sold AS BIGINT) AS sold_quantity,
                       CAST(s.returned AS BIGINT) AS returned_quantity,
                       CAST(s.adjustment_delta AS BIGINT) AS adjustment_delta,
                       CAST(s.closing AS BIGINT) AS closing_quantity,
                       TO_TIMESTAMP(CAST(s.generated_at AS DOUBLE) / 1000.0) AS generated_at
                FROM (SELECT * FROM %s WHERE %s) s
                LEFT JOIN %sproducts p ON p.product_id = CAST(s.product_id AS BIGINT)
                ORDER BY s.snapshot_date, s.product_id
                """.formatted(stockTable, predicate, mysql);
        Map<String, Object> result = drill.query(sql, 10000);
        // Separate coverage avoids mistaking row truncation or missing catalog entries for absent dates.
        Map<String, Object> coverage = drill.query("SELECT CAST(CAST(TO_TIMESTAMP(CAST(snapshot_date AS BIGINT) * 86400) AS DATE) AS VARCHAR) AS snapshot_date, "
                + "COUNT(*) AS snapshot_count, MIN(TO_TIMESTAMP(CAST(generated_at AS DOUBLE) / 1000.0)) AS earliest_generated_at, "
                + "MAX(TO_TIMESTAMP(CAST(generated_at AS DOUBLE) / 1000.0)) AS latest_generated_at FROM " + stockTable + " WHERE " + predicate
                + " GROUP BY snapshot_date ORDER BY snapshot_date", 31);
        List<String> observedDates = new ArrayList<>();
        if (coverage.get("rows") instanceof List<?> rows) {
            for (Object value : rows) {
                if (value instanceof Map<?, ?> row && row.get("snapshot_date") != null) {
                    observedDates.add(row.get("snapshot_date").toString());
                }
            }
        }
        List<String> missingDates = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!observedDates.contains(day.toString())) missingDates.add(day.toString());
        }
        result.put("sourceNames", List.of("Cassandra: " + stockTable + " (direct)", "MySQL: " + mysql + "products"));
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("stockMeaning", "Historical end-of-day physical remaining stock including expired/inactive batches and excluding damaged stock; not available/usable stock");
        result.put("catalogMeaning", "Labels come from the current MySQL catalog; snapshots without a matching product retain null labels");
        result.put("missingSnapshotDates", missingDates);
        result.put("snapshotCoverage", coverage.get("rows"));
        result.put("coverageSql", coverage.get("sql"));
        result.put("snapshotCoverageMeaning", "Missing dates have no snapshot rows at the coverage read; observed dates do not prove all products are covered. No balances are filled or generated; the two reads are not atomic");
        result.put("snapshotBatchStatus", "UNVERIFIED");
        result.put("scanMeaning", "Cassandra epoch-day range filtering runs in Drill without partition pruning; the row limit bounds output, not rows scanned");
        result.put("generatedAtMeaning", "generated_at is the snapshot MySQL read cut, not batch completion; mixed timestamps can reflect partial writes or multiple runs. Equal timestamps do not prove the entire batch completed");
        result.put("rowLimit", 10000);
        result.put("mayBeTruncated", result.get("rows") instanceof List<?> rows && rows.size() >= 10000);
        return result;
    }

    public Map<String, Object> paymentLog(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30) {
            throw new BadRequestException("from and to are required; range must be valid and at most 31 Vietnam calendar days");
        }
        List<String> branches = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> references = new ArrayList<>();
        for (LocalDate day = from.minusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            Path partition = paymentLogs.resolve(day.toString());
            boolean present = false;
            if (Files.isDirectory(partition)) {
                try (var files = Files.list(partition)) {
                    present = files.anyMatch(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json"));
                } catch (IOException ex) {
                    throw new BadRequestException("Cannot read POS payment log partition " + day);
                }
            }
            if (!present) { missing.add(day.toString()); continue; }
            String reference = "analytics.pos_payments.`" + day + "/*.json`";
            references.add(reference);
            branches.add("SELECT CAST(payment_record_id AS BIGINT) AS payment_record_id, CAST(order_id AS BIGINT) AS order_id, "
                    + "CAST(event_id AS VARCHAR) AS event_id, CAST(event_type AS VARCHAR) AS event_type, "
                    + "CAST(amount AS DECIMAL(18,2)) AS amount, CAST(payment_method AS VARCHAR) AS payment_method, "
                    + "CAST(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', '') AS TIMESTAMP) AS event_time FROM " + reference);
        }
        if (branches.isEmpty()) {
            throw new BadRequestException("No POS payment JSON logs exist for the requested UTC partitions; no comparison was performed");
        }
        String sql = """
                WITH log_raw AS (%s), log_filtered AS (
                    SELECT * FROM log_raw
                    WHERE event_time >= TIMESTAMP '%s' AND event_time < TIMESTAMP '%s'
                ), logs AS (
                    SELECT payment_record_id, MIN(order_id) AS order_id, MIN(event_type) AS event_type,
                           MIN(amount) AS amount, MIN(payment_method) AS payment_method,
                           MIN(event_time) AS event_time, COUNT(*) AS log_count
                    FROM log_filtered GROUP BY payment_record_id
                ), payments AS (
                    SELECT p.payment_record_id, p.order_id, p.event_type, p.amount, p.payment_method, p.created_at
                    FROM %spayment_records p JOIN %ssales_orders o ON o.order_id = p.order_id
                    WHERE o.order_type = 'IN_STORE'
                      AND p.created_at >= TIMESTAMP '%s' AND p.created_at < TIMESTAMP '%s'
                )
                SELECT COALESCE(p.payment_record_id, l.payment_record_id) AS payment_record_id,
                       p.order_id AS mysql_order_id, l.order_id AS log_order_id,
                       p.event_type AS mysql_event_type, l.event_type AS log_event_type,
                       p.amount AS mysql_amount, l.amount AS log_amount,
                       p.created_at AS mysql_created_at, l.event_time AS log_event_time,
                       p.payment_method AS mysql_payment_method, l.payment_method AS log_payment_method,
                       COALESCE(l.log_count, 0) AS log_count,
                       COALESCE(p.amount, 0) - COALESCE(l.amount, 0) AS amount_delta,
                       CASE WHEN p.payment_record_id IS NOT NULL AND l.payment_record_id IS NOT NULL
                                  AND l.log_count = 1 AND p.order_id = l.order_id
                                  AND ABS(p.amount - l.amount) <= 0.01
                                  AND p.payment_method = l.payment_method
                                  AND ((p.event_type = 'PAYMENT' AND l.event_type = 'PAYMENT_RECEIVED')
                                    OR (p.event_type = 'REFUND' AND l.event_type = 'REFUND_CONFIRMED'))
                            THEN 'MATCH' ELSE 'CHECK' END AS comparison_status
                FROM payments p FULL OUTER JOIN logs l ON l.payment_record_id = p.payment_record_id
                ORDER BY payment_record_id
                """.formatted(String.join(" UNION ALL ", branches), ReportTime.utcStart(from), ReportTime.utcStart(to.plusDays(1)),
                        mysql, mysql, ReportTime.utcStart(from), ReportTime.utcStart(to.plusDays(1)));
        Map<String, Object> result = drill.query(sql, 10000);
        result.put("sourceNames", List.of("JSON: analytics.pos_payments (UTC partitions)", "MySQL: payment_records, sales_orders"));
        result.put("dataSources", references);
        result.put("missingUtcPartitions", missing);
        result.put("comparisonMeaning", "MATCH compares one logged record with MySQL by payment_record_id, order, event type, method and amount; it does not certify log completeness or timestamp equality");
        result.put("rowLimit", 10000);
        result.put("from", from.toString());
        result.put("to", to.toString());
        return result;
    }
}
