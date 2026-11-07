package com.api.supermarket.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;

/**
 * MongoDB là nguồn sự thật: transaction nhiều document, write/read concern majority
 * (spec mục 19.5). Tiền lưu Decimal128 qua thuộc tính
 * {@code spring.data.mongodb.representation.big-decimal=decimal128}.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoClientCustomizer() {
        return builder -> builder
                .writeConcern(WriteConcern.MAJORITY)
                .readConcern(ReadConcern.MAJORITY)
                // Mongo không tới được thì báo lỗi sau 5 giây thay vì 30 giây mặc định.
                .applyToClusterSettings(c -> c.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s.connectTimeout(5, TimeUnit.SECONDS));
    }
}
