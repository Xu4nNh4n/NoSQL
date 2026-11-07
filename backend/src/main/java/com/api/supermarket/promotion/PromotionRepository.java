package com.api.supermarket.promotion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.api.supermarket.config.MongoIndexContributor;

@Repository
public class PromotionRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public PromotionRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        template.indexOps(Promotion.COLLECTION).createIndex(new Index().on("active", Sort.Direction.ASC)
                .on("endsAt", Sort.Direction.ASC).named("active_endsAt"));
    }

    public Promotion save(Promotion p) {
        return mongo.save(p);
    }

    public Optional<Promotion> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Promotion.class));
    }

    /** Đang bật và chưa kết thúc (có thể chưa bắt đầu); engine lọc theo thời điểm bán. */
    public List<Promotion> findActiveNotEnded(Instant now) {
        return mongo.find(Query.query(Criteria.where("active").is(true).and("endsAt").gt(now)), Promotion.class);
    }

    public Page<Promotion> search(Promotion.Type type, Boolean active, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        if (type != null) {
            filters.add(Criteria.where("type").is(type));
        }
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = mongo.count(query, Promotion.class);
        return new PageImpl<>(mongo.find(query.with(pageable), Promotion.class), pageable, total);
    }
}
