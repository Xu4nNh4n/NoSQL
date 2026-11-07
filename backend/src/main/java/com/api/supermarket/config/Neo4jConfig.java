package com.api.supermarket.config;

import java.util.concurrent.TimeUnit;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Driver Neo4j chỉ được tạo khi có NEO4J_URI. Tạo driver không mở kết nối ngay,
 * nên Neo4j tắt thì ứng dụng vẫn khởi động. Spring gọi {@code close()} khi tắt.
 */
@Configuration
public class Neo4jConfig {

    @Bean
    @ConditionalOnExpression("!'${app.neo4j.uri:}'.isBlank()")
    public Driver neo4jDriver(
            @Value("${app.neo4j.uri}") String uri,
            @Value("${app.neo4j.username:neo4j}") String username,
            @Value("${app.neo4j.password:}") String password) {
        Config config = Config.builder()
                .withConnectionTimeout(5, TimeUnit.SECONDS)
                .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
                .build();
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
    }
}
