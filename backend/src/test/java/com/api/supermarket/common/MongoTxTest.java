package com.api.supermarket.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

class MongoTxTest {
    private final MongoTx tx = new MongoTx(TransactionOperations.withoutTransaction());

    @Test
    void retriesConditionalUpdateConflicts() {
        AtomicInteger calls = new AtomicInteger();
        String result = tx.run(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RetryableConflictException("lô vừa bị lấy");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls.get());
    }

    @Test
    void givesUpAfterThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RetryableConflictException.class, () -> tx.run(() -> {
            calls.incrementAndGet();
            throw new RetryableConflictException("x");
        }));
        assertEquals(MongoTx.MAX_ATTEMPTS, calls.get());
    }

    @Test
    void businessErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> tx.run(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("thiếu tồn");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void recognisesMongoTransientErrorsEvenWhenWrapped() {
        MongoException transientError = new MongoException("tx aborted");
        transientError.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        assertTrue(MongoTx.isRetryable(new RuntimeException("wrapped", transientError)));

        MongoCommandException writeConflict = new MongoCommandException(
                BsonDocument.parse("{ok: 0, code: 112, errmsg: 'WriteConflict'}"), new ServerAddress());
        assertTrue(MongoTx.isRetryable(writeConflict));
        assertFalse(MongoTx.isRetryable(new MongoException("other")));
    }
}
