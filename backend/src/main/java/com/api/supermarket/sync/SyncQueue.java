package com.api.supermarket.sync;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.api.supermarket.common.AppTime;

/**
 * Điểm vào duy nhất để xếp việc đồng bộ. Gọi <b>bên trong</b> transaction nghiệp vụ:
 * bản ghi sync_queue commit cùng đơn hàng/lô hàng, nên không bao giờ mất việc cần ghi.
 * Sau commit, việc được xử lý ngay trên luồng nền; nếu lỗi, {@link SyncJob} thử lại.
 */
@Component
public class SyncQueue {
    private static final Logger log = LoggerFactory.getLogger(SyncQueue.class);

    private final SyncTaskRepository repository;
    private final SyncService service;
    private final TaskExecutor executor;
    private final AppTime time;

    public SyncQueue(SyncTaskRepository repository, SyncService service,
            @Qualifier("syncExecutor") TaskExecutor executor, AppTime time) {
        this.repository = repository;
        this.service = service;
        this.executor = executor;
        this.time = time;
    }

    public SyncTask enqueue(String type, String refId, Document payload, String ownerId) {
        SyncTask task = repository.insert(new SyncTask(null, type, refId, payload, SyncStatus.PENDING, 0, null,
                time.now(), null, ownerId));
        String id = task.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(id);
                }
            });
        } else {
            dispatch(id);
        }
        return task;
    }

    private void dispatch(String id) {
        try {
            executor.execute(() -> service.process(id));
        } catch (RuntimeException ex) {
            // Hàng đợi luồng nền đầy: bỏ qua, job định kỳ sẽ xử lý.
            log.warn("Không xếp được việc đồng bộ {} lên luồng nền: {}", id, ex.toString());
        }
    }
}
