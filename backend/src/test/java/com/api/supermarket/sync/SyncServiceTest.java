package com.api.supermarket.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.api.supermarket.common.AppTime;

class SyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");

    private SyncTaskRepository repository;
    private final AppTime time = new AppTime(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));

    /** Handler giả: ghi lại các lần gọi, có thể cấu hình để ném lỗi. */
    static class FakeHandler implements SyncHandler {
        final String type;
        final boolean fail;
        final List<String> calls = new ArrayList<>();

        FakeHandler(String type, boolean fail) {
            this.type = type;
            this.fail = fail;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public void apply(SyncTask task) {
            calls.add(task.getId());
            if (fail) {
                throw new IllegalStateException("cassandra down");
            }
        }
    }

    @BeforeEach
    void setUp() {
        repository = mock(SyncTaskRepository.class);
    }

    private SyncService service(SyncHandler... handlers) {
        return new SyncService(repository, List.of(handlers), time, 10);
    }

    private void givenTask(String id, String type, SyncStatus status, int attempts) {
        when(repository.findById(id)).thenReturn(Optional.of(
                new SyncTask(id, type, "ref", null, status, attempts, null, NOW, null, "u1")));
    }

    @Test
    void successMarksDone() {
        givenTask("t1", "ORDER_CREATED", SyncStatus.PENDING, 0);
        FakeHandler h = new FakeHandler("ORDER_CREATED", false);
        assertTrue(service(h).process("t1"));
        assertEquals(List.of("t1"), h.calls);
        verify(repository).markDone("t1", NOW);
    }

    @Test
    void handlerErrorKeepsPendingAndCountsAttempt() {
        givenTask("t1", "ORDER_CREATED", SyncStatus.PENDING, 0);
        assertFalse(service(new FakeHandler("ORDER_CREATED", true)).process("t1"));
        verify(repository).recordFailure(eq("t1"), anyString(), eq(false));
        verify(repository, never()).markDone(anyString(), any());
    }

    @Test
    void tenthFailureGivesUp() {
        givenTask("t1", "ORDER_CREATED", SyncStatus.PENDING, 9);
        service(new FakeHandler("ORDER_CREATED", true)).process("t1");
        verify(repository).recordFailure(eq("t1"), anyString(), eq(true));
    }

    @Test
    void missingHandlerFailsImmediately() {
        givenTask("t1", "UNKNOWN", SyncStatus.PENDING, 0);
        assertFalse(service(new FakeHandler("ORDER_CREATED", false)).process("t1"));
        verify(repository).recordFailure("t1", "no handler", true);
    }

    @Test
    void doneTaskIsNotProcessedAgain() {
        givenTask("t1", "ORDER_CREATED", SyncStatus.DONE, 0);
        FakeHandler h = new FakeHandler("ORDER_CREATED", false);
        assertFalse(service(h).process("t1"));
        assertTrue(h.calls.isEmpty());
        verify(repository, never()).markDone(anyString(), any());
        verify(repository, never()).recordFailure(anyString(), anyString(), anyBoolean());
    }

    @Test
    void oneFailingHandlerOfManyPreventsDone() {
        givenTask("t1", "ORDER_CREATED", SyncStatus.PENDING, 0);
        FakeHandler cassandra = new FakeHandler("ORDER_CREATED", false);
        FakeHandler neo4j = new FakeHandler("ORDER_CREATED", true);
        assertFalse(service(cassandra, neo4j).process("t1"));
        verify(repository, never()).markDone(anyString(), any());
        verify(repository).recordFailure(eq("t1"), anyString(), eq(false));
    }

    @Test
    void processPendingCountsOnlySuccesses() {
        givenTask("a", "OK", SyncStatus.PENDING, 0);
        givenTask("b", "BAD", SyncStatus.PENDING, 0);
        when(repository.findPendingOlderThan(NOW, 200)).thenReturn(List.of(
                new SyncTask("a", "OK", null, null, SyncStatus.PENDING, 0, null, NOW, null, null),
                new SyncTask("b", "BAD", null, null, SyncStatus.PENDING, 0, null, NOW, null, null)));
        int done = service(new FakeHandler("OK", false), new FakeHandler("BAD", true)).processPending(NOW, 200);
        assertEquals(1, done);
    }
}
