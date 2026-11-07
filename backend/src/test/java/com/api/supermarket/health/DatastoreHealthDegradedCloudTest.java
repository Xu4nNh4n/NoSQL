package com.api.supermarket.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.api.supermarket.health.DatastoreHealthService.DatastoreStatus;
import com.api.supermarket.support.CloudTest;

/** NFR-09: Redis và Neo4j sai địa chỉ thì ứng dụng vẫn khởi động, trang trạng thái báo đúng. */
@CloudTest
@TestPropertySource(properties = {
        "spring.data.redis.url=redis://127.0.0.1:1",
        "app.neo4j.uri=neo4j://127.0.0.1:1"
})
class DatastoreHealthDegradedCloudTest {
    @Autowired
    DatastoreHealthService health;

    @Test
    void secondaryStoresDownButAppStillRuns() {
        long start = System.nanoTime();
        List<DatastoreStatus> result = health.check();
        long tookMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        Map<String, Boolean> up = result.stream().collect(Collectors.toMap(DatastoreStatus::name, DatastoreStatus::up));

        assertTrue(tookMs <= 4_000, "check() took " + tookMs + " ms");
        assertEquals(Map.of("MongoDB", true, "Cassandra", true, "Neo4j", false, "Redis", false), up);
    }
}
