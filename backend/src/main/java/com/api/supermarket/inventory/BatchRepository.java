package com.api.supermarket.inventory;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.api.supermarket.config.MongoIndexContributor;

/** Truy cập {@code batches} (AP-07 FEFO, AP-08 lô hết hạn). */
@Repository
public class BatchRepository implements MongoIndexContributor {
    static final Sort FEFO_ORDER = Sort.by("expirySortKey", "receivedAt", "batchCode");

    private final MongoTemplate mongo;

    public BatchRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var ops = template.indexOps(Batch.COLLECTION);
        ops.createIndex(new Index().on("batchCode", Sort.Direction.ASC).unique().named("batchCode_unique"));
        // AP-07: Equality productId, Sort theo FEFO; remainingQty lọc sau trên ít document
        ops.createIndex(new Index().on("productId", Sort.Direction.ASC).on("expirySortKey", Sort.Direction.ASC)
                .on("receivedAt", Sort.Direction.ASC).on("batchCode", Sort.Direction.ASC).named("product_fefo"));
        // AP-08 và aggregation tồn khả dụng
        ops.createIndex(new Index().on("expirySortKey", Sort.Direction.ASC).on("remainingQty", Sort.Direction.ASC)
                .named("expiry_remaining"));
        ops.createIndex(new Index().on("supplierId", Sort.Direction.ASC).named("supplierId"));
    }

    public Batch insert(Batch batch) {
        return mongo.insert(batch);
    }

    public Optional<Batch> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Batch.class));
    }

    public Optional<Batch> findByCode(String code) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("batchCode").is(code)), Batch.class));
    }

    /** Lô còn tồn, còn hạn (hạn ≥ hôm nay), theo thứ tự FEFO. */
    public List<Batch> findAvailableForFefo(String productId, LocalDate today) {
        Query query = Query.query(Criteria.where("productId").is(productId).and("expirySortKey").gte(today)
                .and("remainingQty").gt(0)).with(FEFO_ORDER);
        return mongo.find(query, Batch.class);
    }

    /** Trừ tồn lô chỉ khi còn đủ ({@code remainingQty >= qty}); trả false nếu giao dịch khác đã lấy trước. */
    public boolean decrementIfEnough(String batchId, int qty) {
        Query query = Query.query(Criteria.where("_id").is(batchId).and("remainingQty").gte(qty));
        return mongo.updateFirst(query, new Update().inc("remainingQty", -qty), Batch.class).getModifiedCount() == 1;
    }

    /** Hàng trả về lô: {@code restock} cộng tồn bán, ngược lại cộng hàng hỏng. */
    public void returnToBatch(String batchId, int qty, boolean restock) {
        Update update = restock ? new Update().inc("remainingQty", qty) : new Update().inc("damagedQty", qty);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(batchId)), update, Batch.class);
    }

    public void pushAdjustment(String batchId, Batch.Adjustment adjustment) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(batchId)), new Update().push("adjustments", adjustment),
                Batch.class);
    }

    public List<Batch> findExpired(LocalDate today) {
        Query query = Query.query(Criteria.where("expirySortKey").lt(today).and("remainingQty").gt(0))
                .with(Sort.by("expirySortKey"));
        return mongo.find(query, Batch.class);
    }

    public Page<Batch> search(String productId, Pageable pageable) {
        Query query = productId == null || productId.isBlank() ? new Query()
                : Query.query(Criteria.where("productId").is(productId));
        long total = mongo.count(query, Batch.class);
        return new PageImpl<>(mongo.find(query.with(pageable), Batch.class), pageable, total);
    }

    /** Tồn khả dụng theo sản phẩm = tổng tồn còn lại của các lô còn hạn ($match → $group). */
    public Map<String, Integer> availableByProduct(LocalDate today) {
        var agg = newAggregation(Batch.class,
                match(Criteria.where("expirySortKey").gte(today).and("remainingQty").gt(0)),
                group("productId").sum("remainingQty").as("available"));
        Map<String, Integer> result = new HashMap<>();
        for (Document d : mongo.aggregate(agg, Document.class)) {
            result.put(String.valueOf(d.get("_id")), ((Number) d.get("available")).intValue());
        }
        return result;
    }
}
