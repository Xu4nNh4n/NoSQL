package com.api.supermarket.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.datastax.oss.driver.api.core.CqlSession;

/** Tạo các bảng Cassandra lúc khởi động nếu còn thiếu. Lỗi chỉ ghi log. */
@Component
@Order(1) // tạo bảng/constraint trước khi seeder ghi dữ liệu
@ConditionalOnProperty(name = "app.schema.init", havingValue = "true")
public class CassandraSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CassandraSchemaInitializer.class);

    private final CassandraSessionProvider cassandra;

    public CassandraSchemaInitializer(CassandraSessionProvider cassandra) {
        this.cassandra = cassandra;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            CqlSession session = cassandra.session();
            for (String cql : ScriptStatements.load("cassandra/schema.cql")) {
                session.execute(cql);
            }
            log.info("Schema Cassandra sẵn sàng (keyspace {})", cassandra.keyspace());
        } catch (RuntimeException ex) {
            log.warn("Không tạo được schema Cassandra: {}", ex.toString());
        }
    }
}
