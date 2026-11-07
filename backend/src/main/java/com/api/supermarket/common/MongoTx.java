package com.api.supermarket.common;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.mongodb.MongoException;

/**
 * Chạy một thao tác nghiệp vụ trong một transaction MongoDB (spec 5.3). Gặp lỗi tạm thời
 * ({@code TransientTransactionError}, {@code WriteConflict}) hoặc {@link RetryableConflictException}
 * thì chạy lại toàn bộ, tối đa 3 lần.
 */
@Component
public class MongoTx {
    private static final Logger log = LoggerFactory.getLogger(MongoTx.class);
    static final int MAX_ATTEMPTS = 3;
    private static final int WRITE_CONFLICT = 112;

    private final TransactionOperations tx;

    @Autowired
    public MongoTx(MongoTransactionManager manager) {
        this(new TransactionTemplate(manager));
    }

    public MongoTx(TransactionOperations tx) {
        this.tx = tx;
    }

    public <T> T run(Supplier<T> work) {
        for (int attempt = 1;; attempt++) {
            try {
                return tx.execute(status -> work.get());
            } catch (RuntimeException ex) {
                if (attempt >= MAX_ATTEMPTS || !isRetryable(ex)) {
                    throw ex;
                }
                log.debug("Transaction xung đột, thử lại lần {}: {}", attempt + 1, ex.toString());
                backoff(attempt);
            }
        }
    }

    public void runVoid(Runnable work) {
        run(() -> {
            work.run();
            return null;
        });
    }

    static boolean isRetryable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof RetryableConflictException) {
                return true;
            }
            if (t instanceof MongoException me && (me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                    || me.getCode() == WRITE_CONFLICT)) {
                return true;
            }
        }
        return false;
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(15L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
