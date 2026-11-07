package com.api.supermarket.stats;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.RedisSafe;
import com.api.supermarket.inventory.Batch;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lô còn tồn sẽ hết hạn trong 3 ngày tới. Job 07:00 hằng ngày (giờ Việt Nam) tính sẵn và đặt vào Redis
 * {@code cache:batches:expiring-soon} 24 giờ, để thủ kho mở ca là thấy ngay danh sách cần xử lý.
 */
@Service
public class ExpiringSoonService {
    private static final Logger log = LoggerFactory.getLogger(ExpiringSoonService.class);
    public static final String KEY = "cache:batches:expiring-soon";
    static final int DAYS = 3;
    static final Duration TTL = Duration.ofHours(24);
    private static final TypeReference<List<Batch>> LIST = new TypeReference<>() {
    };

    private final MongoTemplate mongo;
    private final RedisSafe redis;
    private final JsonMapper json;
    private final AppTime time;

    public ExpiringSoonService(MongoTemplate mongo, RedisSafe redis, JsonMapper json, AppTime time) {
        this.mongo = mongo;
        this.redis = redis;
        this.json = json;
        this.time = time;
    }

    public List<Batch> expiringSoon() {
        Optional<String> cached = redis.get(KEY);
        if (cached.isPresent()) {
            try {
                return json.readValue(cached.get(), LIST);
            } catch (JacksonException ex) {
                log.warn("Cache lô sắp hết hạn hỏng: {}", ex.getOriginalMessage());
            }
        }
        return refresh();
    }

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void morningJob() {
        try {
            int n = refresh().size();
            log.info("Job 07:00: {} lô sẽ hết hạn trong {} ngày", n, DAYS);
        } catch (RuntimeException ex) {
            log.warn("Job lô sắp hết hạn lỗi: {}", ex.toString());
        }
    }

    List<Batch> refresh() {
        List<Batch> list = compute(time.today());
        redis.set(KEY, json.writeValueAsString(list), TTL);
        return list;
    }

    List<Batch> compute(LocalDate today) {
        Query q = Query.query(Criteria.where("expirySortKey").gte(today).lte(today.plusDays(DAYS))
                .and("remainingQty").gt(0)).with(Sort.by("expirySortKey", "productName"));
        return mongo.find(q, Batch.class);
    }
}
