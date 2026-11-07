package com.api.supermarket.sync;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.api.supermarket.common.AppTime;

/**
 * Chạy các handler của một việc trong {@code sync_queue}. Thành công hết thì DONE;
 * một handler lỗi thì giữ PENDING để job thử lại; lỗi đủ {@code max-attempts} lần thì FAILED.
 */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    public record FailureView(String id, String type, String refId, int attempts, String lastError, Instant createdAt) {
    }

    public record StatusView(long pending, long failed, Instant oldestPendingAt, List<FailureView> recentFailures) {
    }

    private final SyncTaskRepository repository;
    private final Map<String, List<SyncHandler>> handlers;
    private final AppTime time;
    private final int maxAttempts;

    public SyncService(SyncTaskRepository repository, List<SyncHandler> handlers, AppTime time,
            @Value("${app.sync.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.handlers = handlers.stream().collect(Collectors.groupingBy(SyncHandler::type));
        this.time = time;
        this.maxAttempts = maxAttempts;
    }

    /** @return true nếu việc được ghi xong (DONE) trong lần gọi này. */
    public boolean process(String id) {
        SyncTask task = repository.findById(id).orElse(null);
        if (task == null || task.getStatus() != SyncStatus.PENDING) {
            return false;
        }
        List<SyncHandler> forType = handlers.getOrDefault(task.getType(), List.of());
        if (forType.isEmpty()) {
            log.error("Không có handler cho sync type {} (id {})", task.getType(), id);
            repository.recordFailure(id, "no handler", true);
            return false;
        }
        try {
            for (SyncHandler handler : forType) {
                handler.apply(task);
            }
            repository.markDone(id, time.now());
            return true;
        } catch (RuntimeException ex) {
            boolean giveUp = task.getAttempts() + 1 >= maxAttempts;
            log.warn("Đồng bộ {} {} lỗi lần {}{}: {}", task.getType(), id, task.getAttempts() + 1,
                    giveUp ? " (dừng, chờ thử lại tay)" : "", ex.toString());
            repository.recordFailure(id, truncate(ex.toString()), giveUp);
            return false;
        }
    }

    /** Job định kỳ: chỉ lấy việc đủ cũ, để không tranh với lần xử lý ngay sau commit. */
    public int processPending(Instant olderThan, int limit) {
        return processAll(repository.findPendingOlderThan(olderThan, limit));
    }

    /** Chốt ca gọi trước khi đọc sổ thanh toán, để số liệu của thu ngân không thiếu. */
    public int processPendingForOwner(String ownerId) {
        return processAll(repository.findPendingByOwner(ownerId));
    }

    public long retryFailed() {
        return repository.resetFailed();
    }

    public StatusView status() {
        List<FailureView> failures = repository.recentFailures(10).stream()
                .map(t -> new FailureView(t.getId(), t.getType(), t.getRefId(), t.getAttempts(), t.getLastError(),
                        t.getCreatedAt()))
                .toList();
        return new StatusView(
                repository.countByStatus(SyncStatus.PENDING),
                repository.countByStatus(SyncStatus.FAILED),
                repository.oldestPendingAt().orElse(null),
                failures);
    }

    private int processAll(List<SyncTask> tasks) {
        int done = 0;
        for (SyncTask task : tasks) {
            if (process(task.getId())) {
                done++;
            }
        }
        return done;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
