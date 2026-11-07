package com.api.supermarket.inventory;

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

/** Truy cập {@code purchase_orders} (AP-22). */
@Repository
public class PurchaseOrderRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public PurchaseOrderRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var ops = template.indexOps(PurchaseOrder.COLLECTION);
        ops.createIndex(new Index().on("poCode", Sort.Direction.ASC).unique().named("poCode_unique"));
        ops.createIndex(new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                .named("status_createdAt"));
    }

    public PurchaseOrder save(PurchaseOrder po) {
        return mongo.save(po);
    }

    public Optional<PurchaseOrder> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, PurchaseOrder.class));
    }

    public void delete(PurchaseOrder po) {
        mongo.remove(po);
    }

    public Page<PurchaseOrder> search(PurchaseOrder.Status status, String supplierId, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (supplierId != null && !supplierId.isBlank()) {
            filters.add(Criteria.where("supplierId").is(supplierId));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = mongo.count(query, PurchaseOrder.class);
        return new PageImpl<>(mongo.find(query.with(pageable), PurchaseOrder.class), pageable, total);
    }
}
