package com.api.supermarket.stats;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.sales.SalesOrder;

/**
 * Thống kê bán hàng (FR-15, AP-23) bằng MỘT lệnh aggregation:
 * <pre>
 * $match orderedAt ∈ [from, to+1)
 * $facet {
 *   daily:       $group orderDay → số đơn, tổng gốc, giảm giá, thực thu, hoàn
 *   byCategory:  $unwind items → $group categoryId → $lookup categories (tên) → $sort
 *   topProducts: $unwind items → $match không phải quà → $group productId → $sort → $limit 10
 *   totals:      $group null
 * }
 * </pre>
 */
@Service
public class StatsService {
    static final int MAX_DAYS = 366;

    public record DayRow(LocalDate day, long orders, BigDecimal gross, BigDecimal discount, BigDecimal revenue,
            BigDecimal refunds, BigDecimal netRevenue) {
    }

    public record CategoryRow(String categoryId, String categoryName, long quantity, BigDecimal revenue) {
    }

    public record ProductRow(String productId, String sku, String name, long quantity, BigDecimal revenue) {
    }

    public record Totals(long orders, BigDecimal gross, BigDecimal discount, BigDecimal revenue, BigDecimal refunds,
            BigDecimal netRevenue) {
    }

    public record SalesStats(LocalDate from, LocalDate to, Totals totals, List<DayRow> daily, List<CategoryRow> byCategory,
            List<ProductRow> topProducts) {
    }

    private final MongoTemplate mongo;
    private final AppTime time;

    public StatsService(MongoTemplate mongo, AppTime time) {
        this.mongo = mongo;
        this.time = time;
    }

    public SalesStats sales(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? time.today() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("Từ ngày phải trước hoặc bằng đến ngày");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BadRequestException("Khoảng thống kê tối đa " + MAX_DAYS + " ngày");
        }
        Instant fromAt = start.atStartOfDay(time.zone()).toInstant();
        Instant toAt = end.plusDays(1).atStartOfDay(time.zone()).toInstant();
        Document facet = mongo.getCollection(SalesOrder.COLLECTION).aggregate(pipeline(fromAt, toAt)).first();
        return map(start, end, facet);
    }

    static List<Document> pipeline(Instant from, Instant to) {
        Document sums = new Document("orders", new Document("$sum", 1))
                .append("gross", new Document("$sum", "$grossTotal"))
                .append("discount", new Document("$sum", "$discountTotal"))
                .append("revenue", new Document("$sum", "$total"))
                .append("refunds", new Document("$sum", "$refundedTotal"));
        List<Document> daily = List.of(
                new Document("$group", new Document("_id", "$orderDay").append("orders", sums.get("orders"))
                        .append("gross", sums.get("gross")).append("discount", sums.get("discount"))
                        .append("revenue", sums.get("revenue")).append("refunds", sums.get("refunds"))),
                new Document("$sort", new Document("_id", 1)));
        List<Document> byCategory = List.of(
                new Document("$unwind", "$items"),
                new Document("$group", new Document("_id", "$items.categoryId")
                        .append("quantity", new Document("$sum", "$items.quantity"))
                        .append("revenue", new Document("$sum", "$items.netAmount"))),
                new Document("$lookup", new Document("from", Category.COLLECTION)
                        .append("let", new Document("cid", "$_id"))
                        .append("pipeline", List.of(new Document("$match", new Document("$expr",
                                new Document("$eq", List.of("$_id", new Document("$convert",
                                        new Document("input", "$$cid").append("to", "objectId").append("onError", null)
                                                .append("onNull", null))))))))
                        .append("as", "category")),
                new Document("$sort", new Document("revenue", -1)));
        List<Document> top = List.of(
                new Document("$unwind", "$items"),
                new Document("$match", new Document("items.gift", false)),
                new Document("$group", new Document("_id", "$items.productId")
                        .append("sku", new Document("$first", "$items.sku"))
                        .append("name", new Document("$first", "$items.name"))
                        .append("quantity", new Document("$sum", "$items.quantity"))
                        .append("revenue", new Document("$sum", "$items.netAmount"))),
                new Document("$sort", new Document("quantity", -1).append("revenue", -1)),
                new Document("$limit", 10));
        List<Document> totals = List.of(new Document("$group", new Document("_id", null).append("orders", sums.get("orders"))
                .append("gross", sums.get("gross")).append("discount", sums.get("discount"))
                .append("revenue", sums.get("revenue")).append("refunds", sums.get("refunds"))));
        return List.of(
                new Document("$match", new Document("orderedAt", new Document("$gte", Date.from(from)).append("$lt", Date.from(to)))),
                new Document("$facet", new Document("daily", daily).append("byCategory", byCategory)
                        .append("topProducts", top).append("totals", totals)));
    }

    static SalesStats map(LocalDate from, LocalDate to, Document facet) {
        List<DayRow> daily = new ArrayList<>();
        List<CategoryRow> categories = new ArrayList<>();
        List<ProductRow> products = new ArrayList<>();
        Totals totals = new Totals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        if (facet != null) {
            for (Document d : facet.getList("daily", Document.class, List.of())) {
                BigDecimal revenue = money(d.get("revenue"));
                BigDecimal refunds = money(d.get("refunds"));
                daily.add(new DayRow(toDay(d.get("_id")), count(d.get("orders")), money(d.get("gross")),
                        money(d.get("discount")), revenue, refunds, revenue.subtract(refunds)));
            }
            for (Document d : facet.getList("byCategory", Document.class, List.of())) {
                List<Document> cat = d.getList("category", Document.class, List.of());
                categories.add(new CategoryRow(d.get("_id") == null ? null : d.get("_id").toString(),
                        cat.isEmpty() ? "(không rõ)" : cat.get(0).getString("name"), count(d.get("quantity")),
                        money(d.get("revenue"))));
            }
            for (Document d : facet.getList("topProducts", Document.class, List.of())) {
                products.add(new ProductRow(String.valueOf(d.get("_id")), d.getString("sku"), d.getString("name"),
                        count(d.get("quantity")), money(d.get("revenue"))));
            }
            List<Document> t = facet.getList("totals", Document.class, List.of());
            if (!t.isEmpty()) {
                Document d = t.get(0);
                BigDecimal revenue = money(d.get("revenue"));
                BigDecimal refunds = money(d.get("refunds"));
                totals = new Totals(count(d.get("orders")), money(d.get("gross")), money(d.get("discount")), revenue,
                        refunds, revenue.subtract(refunds));
            }
        }
        return new SalesStats(from, to, totals, daily, categories, products);
    }

    /** CSV theo ngày, UTF-8 có BOM để Excel hiển thị đúng tiếng Việt. */
    public static byte[] toCsv(SalesStats stats) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Ngày,Số đơn,Tổng gốc,Giảm giá,Thực thu,Hoàn tiền,Doanh thu thuần\r\n");
        for (DayRow r : stats.daily()) {
            sb.append(r.day()).append(',').append(r.orders()).append(',').append(r.gross().toPlainString()).append(',')
                    .append(r.discount().toPlainString()).append(',').append(r.revenue().toPlainString()).append(',')
                    .append(r.refunds().toPlainString()).append(',').append(r.netRevenue().toPlainString()).append("\r\n");
        }
        Totals t = stats.totals();
        sb.append("Tổng,").append(t.orders()).append(',').append(t.gross().toPlainString()).append(',')
                .append(t.discount().toPlainString()).append(',').append(t.revenue().toPlainString()).append(',')
                .append(t.refunds().toPlainString()).append(',').append(t.netRevenue().toPlainString()).append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static BigDecimal money(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof Decimal128 d) {
            return d.bigDecimalValue();
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }

    private static long count(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    /** Spring Data lưu LocalDate thành Date lúc 0 giờ theo múi giờ máy, nên đổi ngược cũng theo múi giờ máy. */
    private static LocalDate toDay(Object v) {
        if (v instanceof Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return v == null ? null : LocalDate.parse(v.toString());
    }
}
