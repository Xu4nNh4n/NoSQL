package com.api.supermarket.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.api.supermarket.health.DatastoreHealthService.DatastoreStatus;

class DatastoreHealthServiceTest {

    @Test
    void reportsEachStoreIndependentlyWithinTimeout() {
        DatastoreHealthService service = new DatastoreHealthService(List.of(
                DatastoreProbes.probe("MongoDB", "r", () -> { }),
                DatastoreProbes.probe("Cassandra", "r", () -> {
                    throw new IllegalStateException("no host available");
                }),
                DatastoreProbes.probe("Neo4j", "r", () -> Thread.sleep(2_000)),
                DatastoreProbes.probe("Redis", "r", () -> { })),
                Duration.ofMillis(300));

        long start = System.nanoTime();
        List<DatastoreStatus> result = service.check();
        long tookMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(tookMs < 1_500, "check() took " + tookMs + " ms");
        assertEquals(List.of("MongoDB", "Cassandra", "Neo4j", "Redis"), result.stream().map(DatastoreStatus::name).toList());
        assertTrue(result.get(0).up());
        assertNull(result.get(0).error());
        assertFalse(result.get(1).up());
        assertEquals("IllegalStateException: no host available", result.get(1).error());
        assertFalse(result.get(2).up());
        assertEquals("timeout", result.get(2).error());
        assertTrue(result.get(3).up());
        service.destroy();
    }

    @Test
    void errorTextHidesConnectionStrings() {
        String text = DatastoreHealthService.describe(new RuntimeException("wrap",
                new IllegalStateException("cannot reach mongodb+srv://user:secret@cluster0.abc.mongodb.net/db\nmore")));
        assertEquals("IllegalStateException: cannot reach <uri>", text);
    }
}
