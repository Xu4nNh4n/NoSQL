package com.api.supermarket.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;

/**
 * Áp {@code $jsonSchema} trong {@code mongodb/validators.json} lên các collection chính lúc khởi động
 * (spec 19.4). Cùng một file được dùng cho {@code nosql-scripts/mongodb/01-create-collections.js}, nên script và
 * ứng dụng luôn khớp. Mức {@code moderate}: chỉ kiểm tra document mới và document đang hợp lệ.
 */
@Component
public class MongoValidators implements MongoIndexContributor {
    static final String LOCATION = "mongodb/validators.json";

    @Override
    public void ensureIndexes(MongoTemplate mongo) {
        for (Map.Entry<String, Document> e : load().entrySet()) {
            Document validator = new Document("$jsonSchema", e.getValue());
            if (!mongo.collectionExists(e.getKey())) {
                mongo.getDb().createCollection(e.getKey(), new CreateCollectionOptions()
                        .validationOptions(new ValidationOptions().validator(validator)
                                .validationLevel(ValidationLevel.MODERATE).validationAction(ValidationAction.ERROR)));
            } else {
                mongo.getDb().runCommand(new Document("collMod", e.getKey()).append("validator", validator)
                        .append("validationLevel", "moderate").append("validationAction", "error"));
            }
        }
    }

    static Map<String, Document> load() {
        try {
            String json = new ClassPathResource(LOCATION).getContentAsString(StandardCharsets.UTF_8);
            Document all = Document.parse(json);
            Map<String, Document> result = new LinkedHashMap<>();
            for (String name : all.keySet()) {
                result.put(name, all.get(name, Document.class));
            }
            return result;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
