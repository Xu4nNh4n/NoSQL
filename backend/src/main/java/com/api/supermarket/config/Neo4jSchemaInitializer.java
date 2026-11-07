package com.api.supermarket.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Tạo constraint Neo4j lúc khởi động nếu còn thiếu. Không có driver hoặc lỗi thì chỉ ghi log. */
@Component
@ConditionalOnProperty(name = "app.schema.init", havingValue = "true")
public class Neo4jSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);

    private final ObjectProvider<Driver> driver;

    public Neo4jSchemaInitializer(ObjectProvider<Driver> driver) {
        this.driver = driver;
    }

    @Override
    public void run(ApplicationArguments args) {
        Driver neo4j = driver.getIfAvailable();
        if (neo4j == null) {
            log.warn("Chưa cấu hình NEO4J_URI, bỏ qua tạo constraint Neo4j");
            return;
        }
        try (Session session = neo4j.session()) {
            for (String cypher : ScriptStatements.load("neo4j/constraints.cypher")) {
                session.run(cypher).consume();
            }
            log.info("Constraint Neo4j sẵn sàng");
        } catch (RuntimeException ex) {
            log.warn("Không tạo được constraint Neo4j: {}", ex.toString());
        }
    }
}
