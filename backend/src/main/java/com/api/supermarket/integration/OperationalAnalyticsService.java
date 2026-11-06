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
    public enum Report { SALES, EXPIRY, REPLENISHMENT, PROMOTIONS }
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
            throw new BadRequestException("Khoảng ngày Việt Nam phải hợp lệ và tối đa 366 ngày");
        if (top < 1 || top > 500 || !Set.of(7, 14, 30).contains(horizon)
                || leadDays < 1 || leadDays > 90 || coverageDays < leadDays || coverageDays > 180)
            throw new BadRequestException("Top từ 1–500; hạn dùng 7/14/30 ngày; thời gian giao 1–90 ngày; dự trữ từ thời gian giao đến 180 ngày");
        String db = dataset == Dataset.SIMULATED ? "supermarket_sim" : "supermarket_api";
        // Operational stock is a current snapshot. Simulation uses its documented snapshot date.
        LocalDate stockDate = dataset == Dataset.SIMULATED ? simulationSnapshot : ReportTime.today();
        String sales = salesBase(db, from, to);
        String sql = switch (kind) {
            case SALES -> "WITH " + sales + ", totals AS (SELECT SUM(net_quantity) AS total_quantity FROM sales) "
                    + "SELECT s.*, CASE WHEN t.total_quantity > 0 THEN 100.0 * s.net_quantity / t.total_quantity ELSE 0 END AS quantity_share_percent "
                    + "FROM sales s CROSS JOIN totals t ORDER BY net_quantity DESC, sku LIMIT " + top;
            case EXPIRY -> "SELECT p.sku_code AS sku, p.name AS product_name, "
                    + "p.category_name AS category_name, b.batch_code, b.expiry_date, "
                    + "b.remaining_quantity, b.remaining_quantity AS available_quantity, "
                    + "b.unit_cost, b.remaining_quantity * b.unit_cost AS value_at_risk "
                    + "FROM mysql_source." + db + ".inventory_batches b JOIN mysql_source." + db + ".products p ON p.product_id = b.product_id "
                    + "WHERE b.status = 'ACTIVE' AND b.remaining_quantity > 0 AND b.expiry_date >= DATE '" + stockDate
                    + "' AND b.expiry_date <= DATE '" + stockDate.plusDays(horizon) + "' ORDER BY b.expiry_date, p.sku_code, b.batch_code LIMIT 500";
            case REPLENISHMENT -> replenishment(db, to, stockDate, leadDays, coverageDays);
            case PROMOTIONS -> promotions(db, from, to, top);
        };
        int rowLimit = kind == Report.EXPIRY || kind == Report.REPLENISHMENT ? 500 : top;
        Map<String, Object> result = drill.query(sql, rowLimit);
        result.put("reportKind", kind.name());
        result.put("dataset", dataset.name());
        result.put("parameters", Map.of("top", top, "horizon", horizon, "leadDays", leadDays, "coverageDays", coverageDays));
        result.put("sourceNames", List.of("MySQL: " + db));
        result.put("from", from.toString()); result.put("to", to.toString());
        result.put("stockAsOf", stockDate.toString());
        result.put("definition", definition(kind));
        result.put("managementAction", action(kind));
        result.put("assumptions", kind == Report.PROMOTIONS
                ? List.of("Khuyến mãi lấy từ snapshot bất biến trên dòng bán; không đọc bảng khuyến mãi hiện tại. Tên/SKU sản phẩm theo MySQL hiện tại.",
                    "Khoảng ngày là ngày tạo đơn theo giờ Việt Nam, không phải kỳ ghi nhận trả hàng. Lượng trả là tổng đã ghi nhận đến thời điểm truy vấn.",
                    "Giảm giá là số giảm ban đầu trên toàn bộ lượng bán, chưa giảm theo hàng trả; giá trị bán ròng dùng lượng còn lại sau trả. Bao gồm đơn trả toàn bộ và giá ròng bằng 0.",
                    "Số đơn được đếm riêng từng nhóm khuyến mãi/sản phẩm; không cộng thành số đơn duy nhất. Số liệu mô tả sử dụng, không chứng minh tác động nhân quả lên doanh số.")
                : kind == Report.REPLENISHMENT
                ? List.of("Thời gian giao giả định: " + leadDays + " ngày; mục tiêu dự trữ: " + coverageDays + " ngày.",
                    "Tốc độ bán tính đủ 30 ngày kết thúc ở ngày đến, kể cả ngày không bán; không phải dự báo chắc chắn.")
                : List.of("Thông tin nhóm hàng theo catalog hiện tại; lượng trả là tổng đã ghi nhận đến thời điểm truy vấn.",
                    "Mốc ngày/giờ bán hàng là thời điểm tạo đơn theo giờ Việt Nam; tồn kho là ảnh chụp hiện tại, không phải tồn lịch sử."));
        result.put("rowLimit", rowLimit);
        return result;
    }

    private String saleLines(String db, LocalDate from, LocalDate to) {
        return "sale_lines AS (SELECT o.order_id, o.created_at, o.order_type, o.payment_method, i.product_id, COALESCE(i.net_unit_price, i.unit_price) AS unit_price, "
                + "i.quantity - COALESCE(i.returned_quantity, 0) AS net_quantity "
                + "FROM mysql_source." + db + ".sales_order_items i JOIN mysql_source." + db + ".sales_orders o ON o.order_id = i.order_id "
                + "WHERE o.created_at >= TIMESTAMP '" + ReportTime.utcStart(from) + "' AND o.created_at < TIMESTAMP '" + ReportTime.utcStart(to.plusDays(1)) + "' "
                + "AND o.order_type = 'IN_STORE' "
                + "AND o.status IN ('PAID','PARTIALLY_RETURNED','RETURNED'))";
    }

    private String salesBase(String db, LocalDate from, LocalDate to) {
        return saleLines(db, from, to)
                + ", sales AS (SELECT p.product_id, p.sku_code AS sku, "
                + "p.name AS product_name, p.category_name AS category_name, "
                + "CAST(1 AS BIGINT) AS catalog_count, SUM(l.net_quantity) AS net_quantity, "
                + "SUM(l.net_quantity * l.unit_price) AS net_sales_amount "
                + "FROM sale_lines l JOIN mysql_source." + db + ".products p ON p.product_id = l.product_id "
                + "GROUP BY p.product_id, p.sku_code, p.name, p.category_name "
                + "HAVING SUM(l.net_quantity) > 0)";
    }

    private String promotions(String db, LocalDate from, LocalDate to, int top) {
        // Parse in Drill after JDBC projection; only ASCII SQL literals are pushed to MySQL.
        return """
                WITH promoted_lines AS (
                    SELECT o.order_id, i.product_id, p.sku_code AS sku, p.name AS product_name,
                           i.unit_price AS original_unit_price,
                           COALESCE(i.net_unit_price, i.unit_price) AS net_unit_price,
                           i.quantity, COALESCE(i.returned_quantity, 0) AS returned_quantity,
                           CONVERT_FROM(CONVERT_TO(CAST(i.promotion_snapshot AS VARCHAR(65535)), 'UTF8'), 'JSON') AS snapshot
                    FROM mysql_source.%s.sales_order_items i
                    JOIN mysql_source.%s.sales_orders o ON o.order_id = i.order_id
                    JOIN mysql_source.%s.products p ON p.product_id = i.product_id
                    WHERE o.created_at >= TIMESTAMP '%s' AND o.created_at < TIMESTAMP '%s'
                      AND o.order_type = 'IN_STORE' AND o.status IN ('PAID','PARTIALLY_RETURNED','RETURNED')
                      AND i.promotion_snapshot IS NOT NULL
                ), promotions AS (
                    SELECT CAST(l.snapshot.promotionId AS BIGINT) AS promotion_id,
                           CAST(l.snapshot.name AS VARCHAR) AS promotion_name,
                           CAST(l.snapshot.percent AS DECIMAL(5,2)) AS discount_percent,
                           l.order_id, l.product_id, l.sku, l.product_name,
                           l.original_unit_price, l.net_unit_price, l.quantity, l.returned_quantity
                    FROM promoted_lines l
                )
                SELECT promotion_id, promotion_name, discount_percent, product_id, sku, product_name,
                       COUNT(DISTINCT order_id) AS order_count, SUM(quantity) AS sold_quantity,
                       SUM(returned_quantity) AS returned_quantity,
                       SUM(quantity - returned_quantity) AS net_quantity,
                       SUM((original_unit_price - net_unit_price) * quantity) AS discount_amount,
                       SUM(net_unit_price * (quantity - returned_quantity)) AS net_sales_amount
                FROM promotions
                GROUP BY promotion_id, promotion_name, discount_percent, product_id, sku, product_name
                ORDER BY discount_amount DESC, promotion_id, sku, promotion_name, discount_percent
                LIMIT %d
                """.formatted(db, db, db, ReportTime.utcStart(from), ReportTime.utcStart(to.plusDays(1)), top);
    }

    private String replenishment(String db, LocalDate to, LocalDate today, int lead, int coverage) {
        return "WITH " + salesBase(db, to.minusDays(29), to) + ", stock AS (SELECT product_id, "
                + "SUM(CASE WHEN status = 'ACTIVE' AND (expiry_date IS NULL OR expiry_date >= DATE '" + today
                + "') THEN remaining_quantity ELSE 0 END) AS usable_quantity FROM mysql_source." + db + ".inventory_batches GROUP BY product_id), "
                + "balances AS (SELECT p.sku_code AS sku, p.name AS product_name, "
                + "p.category_name AS category_name, COALESCE(b.usable_quantity,0) AS usable_quantity, "
                + "COALESCE(s.net_quantity,0) / 30.0 AS daily_sales, p.reorder_point FROM mysql_source." + db + ".products p "
                + "LEFT JOIN stock b ON b.product_id = p.product_id LEFT JOIN sales s ON s.product_id = p.product_id "
                + "WHERE p.is_active = TRUE) "
                + "SELECT *, CASE WHEN daily_sales > 0 THEN usable_quantity / daily_sales ELSE NULL END AS days_of_stock, "
                + "CASE WHEN daily_sales > 0 THEN GREATEST(0, CEIL(daily_sales * " + coverage + " - usable_quantity)) ELSE 0 END AS suggested_quantity, "
                + "CASE WHEN daily_sales = 0 THEN 'NO_DEMAND' WHEN usable_quantity < daily_sales * " + lead
                + " THEN 'REORDER' ELSE 'SUFFICIENT' END AS stock_status FROM balances ORDER BY suggested_quantity DESC, daily_sales DESC, sku LIMIT 500";
    }

    private String definition(Report report) {
        return switch (report) {
            case SALES -> "Xếp sản phẩm theo số lượng bán ròng giảm dần: số đã mua − số đã nhận trả. Tỷ trọng số lượng = số lượng bán ròng của sản phẩm / tổng số lượng bán ròng của mọi sản phẩm trong kỳ, trước khi giới hạn Top. Sản phẩm bằng số lượng được sắp tiếp theo SKU. Cột giá trị bán chỉ để tham khảo, không quyết định thứ hạng.";
            case PROMOTIONS -> "Thống kê khuyến mãi theo snapshot trên dòng bán: giảm giá ban đầu = tổng (giá gốc − giá ròng) × lượng bán, chưa trừ hàng trả; giá trị bán ròng = tổng giá ròng × (lượng bán − lượng trả). Đếm cả đơn trả toàn bộ và khuyến mãi 100% có giá ròng bằng 0. Đây không phải tiền thực thu hay lợi nhuận.";
            case EXPIRY -> "Giá trị có nguy cơ = lượng còn trong lô × giá vốn lô, gồm cả phần đã giữ; chỉ lô ACTIVE sắp hết hạn, chưa tính hàng đã hết hạn.";
            case REPLENISHMENT -> "Tồn có thể bán loại lô hết hạn/cách ly và lượng đã giữ; gợi ý nhập = tối đa(0, làm tròn lên tốc độ bán × ngày dự trữ − tồn khả dụng).";
        };
    }

    private String action(Report report) {
        return switch (report) {
            case PROMOTIONS -> "Đối chiếu số đơn, lượng trả và giảm giá ban đầu để theo dõi sử dụng khuyến mãi; không suy ra hiệu quả nhân quả hoặc lợi nhuận từ báo cáo này.";
            case SALES -> "Theo dõi mặt hàng bán nhiều để xem xét bổ sung tồn; đối chiếu thống kê khuyến mãi khi phân tích việc sử dụng ưu đãi.";
            case EXPIRY -> "Ưu tiên xử lý các lô gần hạn và có giá trị rủi ro lớn; bảo đảm nhặt hàng đúng FEFO.";
            case REPLENISHMENT -> "Xem các SKU REORDER để lên kế hoạch nhập; xác nhận thời gian giao thực tế trước khi đặt nhà cung cấp.";
        };
    }
}
