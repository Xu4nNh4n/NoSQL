package com.api.supermarket.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** Tạo index MongoDB lúc khởi động nếu còn thiếu. Lỗi chỉ ghi log, không chặn khởi động. */
@Component
@ConditionalOnProperty(name = "app.schema.init", havingValue = "true")
public class MongoIndexInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoTemplate mongo;
    private final List<MongoIndexContributor> contributors;

    public MongoIndexInitializer(MongoTemplate mongo, List<MongoIndexContributor> contributors) {
        this.mongo = mongo;
        this.contributors = contributors;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (MongoIndexContributor contributor : contributors) {
            try {
                contributor.ensureIndexes(mongo);
            } catch (RuntimeException ex) {
                log.warn("Không tạo được index MongoDB ({}): {}", contributor.getClass().getSimpleName(), ex.toString());
            }
        }
    }
}
