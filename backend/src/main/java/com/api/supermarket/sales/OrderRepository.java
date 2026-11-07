package com.api.supermarket.sales;

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

/** Truy cập {@code orders} (AP-09, AP-10, AP-11) và {@code returns}. */
@Repository
public class OrderRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public OrderRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var orders = template.indexOps(SalesOrder.COLLECTION);
        orders.createIndex(new Index().on("orderCode", Sort.Direction.ASC).unique().named("orderCode_unique"));
        orders.createIndex(new Index().on("requestKey", Sort.Direction.ASC).unique().named("requestKey_unique"));
        orders.createIndex(new Index().on("orderedAt", Sort.Direction.DESC).named("orderedAt"));
        orders.createIndex(new Index().on("cashier.id", Sort.Direction.ASC).on("orderedAt", Sort.Direction.DESC)
                .named("cashier_orderedAt"));
        orders.createIndex(new Index().on("status", Sort.Direction.ASC).on("orderedAt", Sort.Direction.DESC)
                .named("status_orderedAt"));
        var returns = template.indexOps(SalesReturn.COLLECTION);
        returns.createIndex(new Index().on("requestKey", Sort.Direction.ASC).unique().named("requestKey_unique"));
        returns.createIndex(new Index().on("orderId", Sort.Direction.ASC).named("orderId"));
    }

    public SalesOrder insert(SalesOrder order) {
        return mongo.insert(order);
    }

    public SalesOrder save(SalesOrder order) {
        return mongo.save(order);
    }

    public Optional<SalesOrder> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, SalesOrder.class));
    }

    public Optional<SalesOrder> findByCode(String code) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("orderCode").is(code)), SalesOrder.class));
    }

    public Optional<SalesOrder> findByRequestKey(String key) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("requestKey").is(key)), SalesOrder.class));
    }

    public Page<SalesOrder> search(Instant from, Instant to, SalesOrder.Status status, SalesOrder.Method method,
            String cashierId, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        if (from != null || to != null) {
            Criteria c = Criteria.where("orderedAt");
            if (from != null) {
                c = c.gte(from);
            }
            if (to != null) {
                c = c.lt(to);
            }
            filters.add(c);
        }
        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (method != null) {
            filters.add(Criteria.where("paymentMethod").is(method));
        }
        if (cashierId != null && !cashierId.isBlank()) {
            filters.add(Criteria.where("cashier.id").is(cashierId));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = mongo.count(query, SalesOrder.class);
        return new PageImpl<>(mongo.find(query.with(pageable), SalesOrder.class), pageable, total);
    }

    public SalesReturn insertReturn(SalesReturn r) {
        return mongo.insert(r);
    }

    public Optional<SalesReturn> findReturnByRequestKey(String key) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("requestKey").is(key)), SalesReturn.class));
    }

    public List<SalesReturn> findReturnsByOrder(String orderId) {
        return mongo.find(Query.query(Criteria.where("orderId").is(orderId)).with(Sort.by("createdAt")), SalesReturn.class);
    }
}
