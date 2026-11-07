package com.api.supermarket.catalog.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.api.supermarket.config.MongoIndexContributor;

/** Truy cập {@code products} (AP-03, AP-04, AP-05). */
@Repository
public class ProductRepository implements MongoIndexContributor {
    /** Không tách gốc từ tiếng Anh; text index v3 không phân biệt hoa thường và dấu. */
    static final String TEXT_LANGUAGE = "none";

    private final MongoTemplate mongo;

    public ProductRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        var ops = template.indexOps(Product.COLLECTION);
        ops.createIndex(new Index().on("sku", Sort.Direction.ASC).unique().named("sku_unique"));
        ops.createIndex(TextIndexDefinition.builder().onField("name", 2F).onField("sku", 3F)
                .withDefaultLanguage(TEXT_LANGUAGE).named("name_sku_text").build());
        // ESR: lọc theo nhóm (Equality) rồi sắp/lọc theo giá (Sort/Range)
        ops.createIndex(new Index().on("categoryId", Sort.Direction.ASC).on("price", Sort.Direction.ASC)
                .named("categoryId_price"));
        ops.createIndex(new Index().on("active", Sort.Direction.ASC).on("name", Sort.Direction.ASC)
                .named("active_name"));
    }

    public Product save(Product product) {
        return mongo.save(product);
    }

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Product.class));
    }

    public Optional<Product> findBySku(String sku) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("sku").is(sku)), Product.class));
    }

    public boolean existsBySku(String sku, String excludeId) {
        Criteria c = Criteria.where("sku").is(sku);
        if (excludeId != null) {
            c = c.and("_id").ne(excludeId);
        }
        return mongo.exists(Query.query(c), Product.class);
    }

    public boolean existsByField(String field, String value) {
        return mongo.exists(Query.query(Criteria.where(field).is(value)), Product.class);
    }

    /** Tên nhóm hàng được chép vào sản phẩm; đổi tên nhóm thì sửa một lượt mọi sản phẩm của nhóm. */
    public long renameCategory(String categoryId, String categoryName) {
        return mongo.updateMulti(Query.query(Criteria.where("categoryId").is(categoryId)),
                new Update().set("categoryName", categoryName), Product.class).getModifiedCount();
    }

    /** Danh sách sản phẩm đang bán cho quầy, chỉ lấy trường cần thiết (projection). */
    public List<Product> findActiveForPos() {
        Query query = Query.query(Criteria.where("active").is(true)).with(Sort.by("name"));
        query.fields().include("sku", "name", "price", "categoryId", "categoryName", "imageUrl", "stockQuantity");
        return mongo.find(query, Product.class);
    }

    public Page<Product> search(String keyword, String categoryId, BigDecimal minPrice, BigDecimal maxPrice,
            Boolean active, Pageable pageable) {
        Query query = new Query();
        if (keyword != null && !keyword.isBlank()) {
            query.addCriteria(TextCriteria.forLanguage(TEXT_LANGUAGE).matching(keyword.trim()));
        }
        List<Criteria> filters = new ArrayList<>();
        if (categoryId != null && !categoryId.isBlank()) {
            filters.add(Criteria.where("categoryId").is(categoryId));
        }
        if (minPrice != null || maxPrice != null) {
            Criteria price = Criteria.where("price");
            if (minPrice != null) {
                price = price.gte(minPrice);
            }
            if (maxPrice != null) {
                price = price.lte(maxPrice);
            }
            filters.add(price);
        }
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }
        long total = mongo.count(query, Product.class);
        return new PageImpl<>(mongo.find(query.with(pageable), Product.class), pageable, total);
    }
}
