package com.api.supermarket.sales;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.RedisSafe;

/**
 * Top bán chạy trong ngày (FR-14, AP-19) bằng sorted set Redis {@code top:sales:{yyyyMMdd}}:
 * mỗi đơn {@code ZINCRBY} số lượng (không tính hàng tặng), xem bằng {@code ZREVRANGE 0 9 WITHSCORES}.
 * Giữ 3 ngày. Redis lỗi thì không chặn bán, trang chỉ ẩn khung top.
 */
@Service
public class TopSalesService {
    static final Duration TTL = Duration.ofDays(3);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    public record TopItem(String productId, String sku, String name, long quantity) {
    }

    public record TopSales(LocalDate day, boolean available, List<TopItem> items) {
    }

    private final RedisSafe redis;
    private final ProductRepository products;
    private final AppTime time;

    public TopSalesService(RedisSafe redis, ProductRepository products, AppTime time) {
        this.redis = redis;
        this.products = products;
        this.time = time;
    }

    static String key(LocalDate day) {
        return "top:sales:" + DAY.format(day);
    }

    public void record(SalesOrder order) {
        String key = key(order.getOrderDay());
        for (SalesOrder.OrderLine l : order.getItems()) {
            if (!l.isGift()) {
                redis.zIncrBy(key, l.getProductId(), l.getQuantity(), TTL);
            }
        }
    }

    public TopSales today(int limit) {
        LocalDate day = time.today();
        boolean available = redis.ping();
        if (!available) {
            return new TopSales(day, false, List.of());
        }
        List<TopItem> items = redis.zRevRangeWithScores(key(day), limit).stream().map(e -> {
            Product p = products.findById(e.member()).orElse(null);
            return new TopItem(e.member(), p == null ? null : p.getSku(), p == null ? "(đã xóa)" : p.getName(),
                    (long) e.score());
        }).toList();
        return new TopSales(day, true, items);
    }
}
