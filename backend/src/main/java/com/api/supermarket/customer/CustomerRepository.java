package com.api.supermarket.customer;

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
public class CustomerRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public CustomerRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var ops = template.indexOps(Customer.COLLECTION);
        ops.createIndex(new Index().on("phone", Sort.Direction.ASC).unique().named("phone_unique"));
        ops.createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email_unique_partial")
                .partial(PartialIndexFilter.of(Criteria.where("email").exists(true))));
    }

    public Customer save(Customer customer) {
        return mongo.save(customer);
    }

    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Customer.class));
    }

    public Optional<Customer> findByPhone(String phone) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("phone").is(phone)), Customer.class));
    }

    public boolean existsByField(String field, String value, String excludeId) {
        Criteria c = Criteria.where(field).is(value);
        if (excludeId != null) {
            c = c.and("_id").ne(excludeId);
        }
        return mongo.exists(Query.query(c), Customer.class);
    }

    /** Tìm theo tên hoặc SĐT; từ khóa đi qua Pattern.quote nên không thành biểu thức regex của client. */
    public Page<Customer> search(String keyword, Boolean active, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(Criteria.where("fullName").regex(p), Criteria.where("phone").regex(p),
                    Criteria.where("email").regex(p)));
        }
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = mongo.count(query, Customer.class);
        return new PageImpl<>(mongo.find(query.with(pageable), Customer.class), pageable, total);
    }
}
