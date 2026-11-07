package com.api.supermarket.catalog.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.api.supermarket.config.MongoIndexContributor;

@Repository
public class CategoryRepository implements MongoIndexContributor {
    private final MongoTemplate mongo;

    public CategoryRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void ensureIndexes(MongoTemplate template) {
        template.indexOps(Category.COLLECTION)
                .createIndex(new Index().on("nameLower", Sort.Direction.ASC).unique().named("nameLower_unique"));
    }

    public Category save(Category category) {
        return mongo.save(category);
    }

    public Optional<Category> findById(String id) {
        return Optional.ofNullable(mongo.findById(id, Category.class));
    }

    public List<Category> findAll(Boolean active) {
        Query query = active == null ? new Query() : Query.query(Criteria.where("active").is(active));
        return mongo.find(query.with(Sort.by("name")), Category.class);
    }

    public boolean existsByNameLower(String nameLower, String excludeId) {
        Criteria c = Criteria.where("nameLower").is(nameLower);
        if (excludeId != null) {
            c = c.and("_id").ne(excludeId);
        }
        return mongo.exists(Query.query(c), Category.class);
    }

    public void delete(Category category) {
        mongo.remove(category);
    }
}
