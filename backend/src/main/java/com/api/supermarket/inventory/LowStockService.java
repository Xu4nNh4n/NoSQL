package com.api.supermarket.inventory;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.RedisSafe;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cảnh báo sắp hết hàng (FR-03 bước 5, AP-06): tồn khả dụng = tổng tồn còn lại của lô còn hạn,
 * tính bằng aggregation trên {@code batches}; sản phẩm có tồn khả dụng ≤ ngưỡng đặt lại thì vào danh sách.
 * Cache Redis 1 phút; bán hoặc nhập lô thì xóa cache.
 */
@Service
public class LowStockService {
    private static final Logger log = LoggerFactory.getLogger(LowStockService.class);
    static final Duration TTL = Duration.ofMinutes(1);
    private static final TypeReference<List<LowStockItem>> LIST = new TypeReference<>() {
    };

    public record LowStockItem(String productId, String sku, String name, int reorderPoint, int available,
            int stockQuantity) {
    }

    private final BatchRepository batches;
    private final ProductRepository products;
    private final RedisSafe redis;
    private final JsonMapper json;
    private final AppTime time;

    public LowStockService(BatchRepository batches, ProductRepository products, RedisSafe redis, JsonMapper json,
            AppTime time) {
        this.batches = batches;
        this.products = products;
        this.redis = redis;
        this.json = json;
        this.time = time;
    }

    public List<LowStockItem> lowStock() {
        Optional<String> cached = redis.get(ProductCache.LOW_STOCK_KEY);
        if (cached.isPresent()) {
            try {
                return json.readValue(cached.get(), LIST);
            } catch (JacksonException ex) {
                log.warn("Cache sắp hết hàng hỏng, tính lại: {}", ex.getOriginalMessage());
            }
        }
        List<LowStockItem> fresh = compute();
        redis.set(ProductCache.LOW_STOCK_KEY, json.writeValueAsString(fresh), TTL);
        return fresh;
    }

    List<LowStockItem> compute() {
        Map<String, Integer> available = batches.availableByProduct(time.today());
        return products.findAllActive().stream()
                .map(p -> new LowStockItem(p.getId(), p.getSku(), p.getName(), p.getReorderPoint(),
                        available.getOrDefault(p.getId(), 0), p.getStockQuantity()))
                .filter(i -> i.available() <= i.reorderPoint())
                .sorted(Comparator.comparingInt(LowStockItem::available).thenComparing(LowStockItem::sku))
                .toList();
    }
}
