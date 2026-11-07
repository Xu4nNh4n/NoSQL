package com.api.supermarket.promotion;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.RedisSafe;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Khuyến mãi đang bật và chưa kết thúc, cache-aside trên Redis 5 phút (AP-17). Sửa khuyến mãi thì xóa cache. */
@Component
public class PromotionCache {
    private static final Logger log = LoggerFactory.getLogger(PromotionCache.class);
    public static final String KEY = "cache:promotions:active";
    static final Duration TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<Promotion>> LIST = new TypeReference<>() {
    };

    private final RedisSafe redis;
    private final PromotionRepository promotions;
    private final JsonMapper json;
    private final AppTime time;

    public PromotionCache(RedisSafe redis, PromotionRepository promotions, JsonMapper json, AppTime time) {
        this.redis = redis;
        this.promotions = promotions;
        this.json = json;
        this.time = time;
    }

    public List<Promotion> active() {
        Optional<String> cached = redis.get(KEY);
        if (cached.isPresent()) {
            try {
                return json.readValue(cached.get(), LIST);
            } catch (JacksonException ex) {
                log.warn("Cache khuyến mãi hỏng, đọc lại MongoDB: {}", ex.getOriginalMessage());
            }
        }
        List<Promotion> fresh = promotions.findActiveNotEnded(time.now());
        redis.set(KEY, json.writeValueAsString(fresh), TTL);
        return fresh;
    }

    public void evict() {
        redis.delete(KEY);
    }
}
