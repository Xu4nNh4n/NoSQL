package com.api.supermarket.user;

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

/** Truy cập collection {@code users} (AP-01). Mọi điều kiện lọc dựng bằng Criteria có kiểu, không nhận toán tử từ client. */
@Repository
public class UserRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public UserRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var ops = template.indexOps(UserAccount.COLLECTION);
        ops.createIndex(new Index().on("username", Sort.Direction.ASC).unique().named("username_unique"));
        ops.createIndex(new Index().on("email", Sort.Direction.ASC).unique().named("email_unique_partial")
                .partial(PartialIndexFilter.of(Criteria.where("email").exists(true))));
        ops.createIndex(new Index().on("phone", Sort.Direction.ASC).unique().named("phone_unique_partial")
                .partial(PartialIndexFilter.of(Criteria.where("phone").exists(true))));
    }

    public UserAccount save(UserAccount user) {
        return mongo.save(user);
    }

    public Optional<UserAccount> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, UserAccount.class));
    }

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("username").is(username)), UserAccount.class));
    }

    public boolean existsByUsername(String username) {
        return mongo.exists(Query.query(Criteria.where("username").is(username)), UserAccount.class);
    }

    /** @param excludeId bỏ qua chính người đang sửa; null khi tạo mới */
    public boolean existsByField(String field, String value, String excludeId) {
        Criteria criteria = Criteria.where(field).is(value);
        if (excludeId != null) {
            criteria = criteria.and("_id").ne(excludeId);
        }
        return mongo.exists(Query.query(criteria), UserAccount.class);
    }

    public long countActiveAdmins() {
        return mongo.count(Query.query(Criteria.where("role").is(Role.ADMIN).and("active").is(true)), UserAccount.class);
    }

    public long count() {
        return mongo.estimatedCount(UserAccount.class);
    }

    /** Tìm theo từ khóa (tên đăng nhập, họ tên, email, SĐT), vai trò, trạng thái; có phân trang và sắp xếp. */
    public Page<UserAccount> search(String keyword, Role role, Boolean active, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("username").regex(p), Criteria.where("fullName").regex(p),
                    Criteria.where("email").regex(p), Criteria.where("phone").regex(p)));
        }
        if (role != null) {
            filters.add(Criteria.where("role").is(role));
        }
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        Query query = filters.isEmpty() ? new Query() : Query.query(new Criteria().andOperator(filters));
        long total = mongo.count(query, UserAccount.class); // đếm trước khi gắn skip/limit của trang
        List<UserAccount> content = mongo.find(query.with(pageable), UserAccount.class);
        return new PageImpl<>(content, pageable, total);
    }
}
