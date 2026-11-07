package com.api.supermarket.catalog.product;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.api.supermarket.catalog.product.ProductDtos.PosProduct;
import com.api.supermarket.common.RedisSafe;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside cho danh sách sản phẩm ở quầy (AP-03): Redis có thì dùng, không có hoặc hỏng thì
 * đọc MongoDB rồi ghi lại. Ghi sản phẩm, đổi tên nhóm hàng, bán và nhập lô thì gọi {@link #evict()}.
 */
@Component
public class ProductCache {
    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);
    public static final String ACTIVE_KEY = "cache:products:active";
    public static final String LOW_STOCK_KEY = "cache:products:low-stock";
    static final Duration ACTIVE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<PosProduct>> LIST = new TypeReference<>() {
    };

    private final RedisSafe redis;
    private final ProductRepository products;
    private final JsonMapper json;

    public ProductCache(RedisSafe redis, ProductRepository products, JsonMapper json) {
        this.redis = redis;
        this.products = products;
        this.json = json;
    }

    public List<PosProduct> activeProducts() {
        Optional<String> cached = redis.get(ACTIVE_KEY);
        if (cached.isPresent()) {
            try {
                return json.readValue(cached.get(), LIST);
            } catch (JacksonException ex) {
                log.warn("Cache sản phẩm hỏng, đọc lại MongoDB: {}", ex.getOriginalMessage());
            }
        }
        List<PosProduct> fresh = products.findActiveForPos().stream().map(PosProduct::from).toList();
        redis.set(ACTIVE_KEY, json.writeValueAsString(fresh), ACTIVE_TTL);
        return fresh;
    }

    public void evict() {
        redis.delete(ACTIVE_KEY, LOW_STOCK_KEY);
    }
}
