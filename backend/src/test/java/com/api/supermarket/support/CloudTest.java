package com.api.supermarket.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Test chạy trên CSDL cloud thật, đọc .env ở thư mục gốc. Mặc định bị loại;
 * chạy bằng {@code ./mvnw test -Pcloud-tests}. Dùng database {@code supermarket_dev}
 * để không đụng dữ liệu demo.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("cloud")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mongodb.uri=${MONGODB_URI}",
        "spring.mongodb.database=supermarket_dev",
        "spring.data.redis.url=${REDIS_URL:redis://localhost:6379}",
        "app.neo4j.uri=${NEO4J_URI:}",
        "app.schema.init=true",
        "app.sync.enabled=false"
})
public @interface CloudTest {
}
