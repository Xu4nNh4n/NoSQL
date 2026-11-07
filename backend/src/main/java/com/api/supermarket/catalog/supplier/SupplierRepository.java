package com.api.supermarket.catalog.supplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.api.supermarket.config.MongoIndexContributor;

@Repository
public class SupplierRepository implements MongoIndexContributor {
    /** Collection lô hàng (kế hoạch 5); dùng để không xóa nhà cung cấp đã có lô. */
    static final String BATCHES = "batches";

    private final MongoTemplate mongo;

    public SupplierRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var ops = template.indexOps(Supplier.COLLECTION);
        ops.createIndex(new Index().on("phone", Sort.Direction.ASC).unique().named("phone_unique_partial")
                .partial(PartialIndexFilter.of(Criteria.where("phone").exists(true))));
        ops.createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email_unique_partial")
                .partial(PartialIndexFilter.of(Criteria.where("email").exists(true))));
    }

    public Supplier save(Supplier supplier) {
        return mongo.save(supplier);
    }

    public Optional<Supplier> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Supplier.class));
    }

    public boolean existsByField(String field, String value, String excludeId) {
        Criteria c = Criteria.where(field).is(value);
        if (excludeId != null) {
            c = c.and("_id").ne(excludeId);
        }
        return mongo.exists(Query.query(c), Supplier.class);
    }

    public boolean hasBatches(String supplierId) {
        return mongo.exists(Query.query(Criteria.where("supplierId").is(supplierId)), BATCHES);
    }

    public Page<Supplier> search(String keyword, Boolean active, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(Criteria.where("name").regex(p), Criteria.where("phone").regex(p),
                    Criteria.where("email").regex(p)));
        }
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = mongo.count(query, Supplier.class);
        return new PageImpl<>(mongo.find(query.with(pageable), Supplier.class), pageable, total);
    }

    public void delete(Supplier supplier) {
        mongo.remove(supplier);
    }
}
