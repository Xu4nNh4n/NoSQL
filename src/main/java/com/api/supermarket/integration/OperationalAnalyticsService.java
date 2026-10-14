package com.api.supermarket.integration;

import com.api.supermarket.exception.BadRequestException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Fixed analytical queries only. Simulation never changes transactional repositories. */
@Service
public class OperationalAnalyticsService {
    public enum Report { SALES, EXPIRY, REPLENISHMENT, ABC, CHANNELS, QUALITY }
    public enum Dataset { OPERATIONS, SIMULATED }
    private final DrillAnalyticsService drill;
    private final LocalDate simulationSnapshot;

    public OperationalAnalyticsService(DrillAnalyticsService drill,
            @Value("${analytics.simulation.snapshot-date:2026-10-14}") String snapshotDate) {
        this.drill = drill;
        this.simulationSnapshot = LocalDate.parse(snapshotDate);
    }

    public Map<String, Object> report(Report kind, Dataset dataset, LocalDate from, LocalDate to,
            int top, int horizon, int leadDays, int coverageDays) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365)
            throw new BadRequestException("Khoảng ngày UTC phải hợp lệ và tối đa 366 ngày");
        if (top < 1 || top > 500 || !Set.of(7, 14, 30).contains(horizon)
                || leadDays < 1 || leadDays > 90 || coverageDays < leadDays || coverageDays > 180)
            throw new BadRequestException("Top từ 1–500; hạn dùng 7/14/30 ngày; thời gian giao 1–90 ngày; dự trữ từ thời gian giao đến 180 ngày");
        String db = dataset == Dataset.SIMULATED ? "supermarket_sim" : "supermarket_api";
        String mongo = dataset == Dataset.SIMULATED ? "supermarket_sim_catalog" : "supermarket_catalog";
        // Operational stock is a current snapshot. Simulation uses its documented snapshot date.
        LocalDate stockDate = dataset == Dataset.SIMULATED ? simulationSnapshot : LocalDate.now();
        String catalog = "catalog AS (SELECT CAST(sku AS VARCHAR) AS sku, MAX(name) AS name, "
                + "MAX(categoryName) AS category_name, COUNT(*) AS catalog_count "
                + "FROM mongo_source." + mongo + ".products_catalog GROUP BY CAST(sku AS VARCHAR))";
        String sales = salesBase(db, mongo, from, to);
        String sql = switch (kind) {
            case SALES -> "WITH " + sales + ", totals AS (SELECT SUM(net_sales_amount) AS total_amount FROM sales) "
                    + "SELECT s.*, CASE WHEN t.total_amount > 0 THEN 100.0 * s.net_sales_amount / t.total_amount ELSE 0 END AS share_percent "
                    + "FROM sales s CROSS JOIN totals t ORDER BY net_sales_amount DESC, sku LIMIT " + top;
            case ABC -> "WITH " + sales + ", ranked AS (SELECT s.*, SUM(net_sales_amount) OVER () AS total_amount, "
                    + "SUM(net_sales_amount) OVER (ORDER BY net_sales_amount DESC, sku ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_amount FROM sales s) "
                    + "SELECT r.*, 100.0 * cumulative_amount / NULLIF(total_amount, 0) AS cumulative_percent, "
                    + "CASE WHEN cumulative_amount - net_sales_amount < total_amount * 0.80 THEN 'A' "
                    + "WHEN cumulative_amount - net_sales_amount < total_amount * 0.95 THEN 'B' ELSE 'C' END AS abc_class "
                    + "FROM ranked r ORDER BY net_sales_amount DESC, sku LIMIT " + top;
            case CHANNELS -> "WITH " + saleLines(db, from, to) + " SELECT CAST(created_at AS DATE) AS event_date, "
                    + "CAST(EXTRACT(HOUR FROM created_at) AS INT) AS hour_utc, order_type, payment_method, "
                    + "COUNT(DISTINCT order_id) AS order_count, SUM(net_quantity) AS net_quantity, "
                    + "SUM(net_quantity * unit_price) AS net_sales_amount FROM sale_lines "
                    + "WHERE net_quantity > 0 GROUP BY CAST(created_at AS DATE), EXTRACT(HOUR FROM created_at), order_type, payment_method "
                    + "ORDER BY event_date, hour_utc, order_type, payment_method LIMIT 10000";
            case EXPIRY -> "WITH " + catalog + " SELECT p.sku_code AS sku, COALESCE(m.name, p.name) AS product_name, "
                    + "COALESCE(m.category_name, 'Chưa có catalog MongoDB') AS category_name, b.batch_code, b.expiry_date, "
                    + "b.remaining_quantity, b.reserved_quantity, b.remaining_quantity - b.reserved_quantity AS available_quantity, "
                    + "b.unit_cost, b.remaining_quantity * b.unit_cost AS value_at_risk "
                    + "FROM mysql_source." + db + ".inventory_batches b JOIN mysql_source." + db + ".products p ON p.product_id = b.product_id "
                    + "LEFT JOIN catalog m ON CAST(p.sku_code AS VARCHAR) = m.sku "
                    + "WHERE b.status = 'ACTIVE' AND b.remaining_quantity > 0 AND b.expiry_date >= DATE '" + stockDate
                    + "' AND b.expiry_date <= DATE '" + stockDate.plusDays(horizon) + "' ORDER BY b.expiry_date, p.sku_code, b.batch_code LIMIT 500";
            case REPLENISHMENT -> replenishment(db, mongo, to, stockDate, leadDays, coverageDays);
            case QUALITY -> quality(db, catalog);
        };
        Map<String, Object> result = drill.query(sql, kind == Report.CHANNELS ? 10000 : kind == Report.EXPIRY ? 500 : kind == Report.QUALITY ? 50 : kind == Report.REPLENISHMENT ? 500 : top);
        result.put("reportKind", kind.name());
        result.put("dataset", dataset.name());
        result.put("sourceNames", kind == Report.CHANNELS ? List.of("MySQL: " + db)
                : List.of("MySQL: " + db, "MongoDB: " + mongo));
        result.put("from", from.toString()); result.put("to", to.toString());
        result.put("stockAsOf", stockDate.toString());
        result.put("definition", definition(kind));
        result.put("managementAction", action(kind));
        result.put("assumptions", kind == Report.REPLENISHMENT
                ? List.of("Thời gian giao giả định: " + leadDays + " ngày; mục tiêu dự trữ: " + coverageDays + " ngày.",
                    "Tốc độ bán tính đủ 30 ngày kết thúc ở ngày đến, kể cả ngày không bán; không phải dự báo chắc chắn.")
                : List.of("Thông tin nhóm hàng theo catalog hiện tại; lượng trả là tổng đã ghi nhận đến thời điểm truy vấn.",
                    "Mốc ngày/giờ bán hàng là thời điểm tạo đơn UTC; tồn kho là ảnh chụp hiện tại, không phải tồn lịch sử."));
        result.put("rowLimit", kind == Report.CHANNELS ? 10000
                : kind == Report.EXPIRY || kind == Report.REPLENISHMENT ? 500
                : kind == Report.QUALITY ? 50 : top);
        return result;
    }

    private String saleLines(String db, LocalDate from, LocalDate to) {
        return "sale_lines AS (SELECT o.order_id, o.created_at, o.order_type, o.payment_method, i.product_id, i.unit_price, "
                + "i.quantity - COALESCE(i.returned_quantity, 0) AS net_quantity "
                + "FROM mysql_source." + db + ".sales_order_items i JOIN mysql_source." + db + ".sales_orders o ON o.order_id = i.order_id "
                + "WHERE o.created_at >= TIMESTAMP '" + from + " 00:00:00' AND o.created_at < TIMESTAMP '" + to.plusDays(1) + " 00:00:00' "
                + "AND ((o.order_type = 'IN_STORE' AND o.status IN ('PAID','PARTIALLY_RETURNED','RETURNED')) "
                + "OR (o.order_type = 'ONLINE' AND o.delivery_status IN ('DELIVERED','RETURNED') "
                + "AND o.status IN ('DELIVERED','PARTIALLY_RETURNED','RETURNED'))))";
    }

    private String salesBase(String db, String mongo, LocalDate from, LocalDate to) {
        return saleLines(db, from, to) + ", catalog AS (SELECT CAST(sku AS VARCHAR) AS sku, MAX(name) AS name, "
                + "MAX(categoryName) AS category_name, COUNT(*) AS catalog_count FROM mongo_source." + mongo
                + ".products_catalog GROUP BY CAST(sku AS VARCHAR)), sales AS (SELECT p.product_id, p.sku_code AS sku, "
                + "COALESCE(m.name, p.name) AS product_name, COALESCE(m.category_name, 'Chưa có catalog MongoDB') AS category_name, "
                + "COALESCE(m.catalog_count, 0) AS catalog_count, SUM(l.net_quantity) AS net_quantity, "
                + "SUM(l.net_quantity * l.unit_price) AS net_sales_amount "
                + "FROM sale_lines l JOIN mysql_source." + db + ".products p ON p.product_id = l.product_id "
                + "LEFT JOIN catalog m ON CAST(p.sku_code AS VARCHAR) = m.sku "
                + "GROUP BY p.product_id, p.sku_code, COALESCE(m.name, p.name), COALESCE(m.category_name, 'Chưa có catalog MongoDB'), m.catalog_count "
                + "HAVING SUM(l.net_quantity) > 0)";
    }

    private String replenishment(String db, String mongo, LocalDate to, LocalDate today, int lead, int coverage) {
        return "WITH " + salesBase(db, mongo, to.minusDays(29), to) + ", stock AS (SELECT product_id, "
                + "SUM(CASE WHEN status = 'ACTIVE' AND (expiry_date IS NULL OR expiry_date >= DATE '" + today
                + "') THEN remaining_quantity - reserved_quantity ELSE 0 END) AS usable_quantity FROM mysql_source." + db + ".inventory_batches GROUP BY product_id), "
                + "balances AS (SELECT p.sku_code AS sku, COALESCE(m.name, p.name) AS product_name, "
                + "COALESCE(m.category_name, 'Chưa có catalog MongoDB') AS category_name, COALESCE(b.usable_quantity,0) AS usable_quantity, "
                + "COALESCE(s.net_quantity,0) / 30.0 AS daily_sales, p.reorder_point FROM mysql_source." + db + ".products p "
                + "LEFT JOIN stock b ON b.product_id = p.product_id LEFT JOIN sales s ON s.product_id = p.product_id "
                + "LEFT JOIN catalog m ON CAST(p.sku_code AS VARCHAR) = m.sku WHERE p.is_active = TRUE) "
                + "SELECT *, CASE WHEN daily_sales > 0 THEN usable_quantity / daily_sales ELSE NULL END AS days_of_stock, "
                + "CASE WHEN daily_sales > 0 THEN GREATEST(0, CEIL(daily_sales * " + coverage + " - usable_quantity)) ELSE 0 END AS suggested_quantity, "
                + "CASE WHEN daily_sales = 0 THEN 'NO_DEMAND' WHEN usable_quantity < daily_sales * " + lead
                + " THEN 'REORDER' ELSE 'SUFFICIENT' END AS stock_status FROM balances ORDER BY suggested_quantity DESC, daily_sales DESC, sku LIMIT 500";
    }

    private String quality(String db, String catalog) {
        return "WITH " + catalog + " SELECT 'MYSQL_SKU_MISSING_MONGO' AS metric, COUNT(*) AS issue_count "
                + "FROM mysql_source." + db + ".products p LEFT JOIN catalog m ON CAST(p.sku_code AS VARCHAR) = m.sku WHERE m.sku IS NULL "
                + "UNION ALL SELECT 'MONGO_SKU_MISSING_MYSQL', COUNT(*) FROM catalog m LEFT JOIN mysql_source." + db
                + ".products p ON CAST(p.sku_code AS VARCHAR) = m.sku WHERE p.product_id IS NULL "
                + "UNION ALL SELECT 'MONGO_DUPLICATE_SKU', COALESCE(SUM(catalog_count - 1),0) FROM catalog WHERE catalog_count > 1 "
                + "UNION ALL SELECT 'INVALID_BATCH_BALANCE', COUNT(*) FROM mysql_source." + db
                + ".inventory_batches WHERE remaining_quantity < 0 OR reserved_quantity < 0 OR reserved_quantity > remaining_quantity "
                + "UNION ALL SELECT 'INVALID_RETURN_QUANTITY', COUNT(*) FROM mysql_source." + db
                + ".sales_order_items WHERE returned_quantity < 0 OR returned_quantity > quantity";
    }

    private String definition(Report report) {
        return switch (report) {
            case SALES, ABC, CHANNELS -> "Giá trị món sau trả = (lượng đã mua − lượng đã trả) × đơn giá lưu trên dòng bán; chưa phân bổ giảm giá cấp đơn. Chỉ gồm POS hoàn tất và online đã giao/trả. Đây không phải tiền thực thu hoặc lợi nhuận.";
            case EXPIRY -> "Giá trị có nguy cơ = lượng còn trong lô × giá vốn lô, gồm cả phần đã giữ; chỉ lô ACTIVE sắp hết hạn, chưa tính hàng đã hết hạn.";
            case REPLENISHMENT -> "Tồn có thể bán loại lô hết hạn/cách ly và lượng đã giữ; gợi ý nhập = tối đa(0, làm tròn lên tốc độ bán × ngày dự trữ − tồn khả dụng).";
            case QUALITY -> "Đếm lệch catalog, SKU trùng và số dư kho/trả không hợp lệ; số 0 chỉ xác nhận các quy tắc này, không chứng minh toàn bộ dữ liệu đúng.";
        };
    }

    private String action(Report report) {
        return switch (report) {
            case SALES -> "Ưu tiên nhóm hàng bán tốt; xem SKU thiếu catalog trước khi kết luận cơ cấu nhóm.";
            case ABC -> "Nhóm A cần theo dõi sát; nhóm B và C quản lý theo mức đóng góp. Ngưỡng 80%/95% là quy ước, có thể vượt ở SKU cắt ngưỡng.";
            case CHANNELS -> "Sắp xếp nhân sự theo giờ UTC/kênh; không cộng số đơn các nhóm giờ/kênh thành số khách duy nhất.";
            case EXPIRY -> "Ưu tiên xử lý các lô gần hạn và có giá trị rủi ro lớn; bảo đảm nhặt hàng đúng FEFO.";
            case REPLENISHMENT -> "Xem các SKU REORDER để lên kế hoạch nhập; xác nhận thời gian giao thực tế trước khi đặt nhà cung cấp.";
            case QUALITY -> "Bổ sung catalog còn thiếu và kiểm tra nguồn gây lệch; không tự sửa kho hoặc giao dịch từ màn hình phân tích.";
        };
    }
}
