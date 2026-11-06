package com.api.supermarket.integration;

import com.api.supermarket.repository.DataSyncOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataSyncDispatcher {
    private static final Logger log = LoggerFactory.getLogger(DataSyncDispatcher.class);
    private final DataSyncOutboxRepository outbox;
    private final PosPaymentLogWriter paymentLog;
    private final AstraEventStore astra;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final AtomicBoolean running = new AtomicBoolean();
    @Value("${astra.enabled:false}") private boolean astraEnabled;
    @Value("${integration.sync.enabled:true}") private boolean enabled;
    @Value("${integration.sync.batch-size:25}") private int batchSize;
    @Value("${integration.local-bootstrap:false}") private boolean bootstrapping;

    public DataSyncDispatcher(DataSyncOutboxRepository outbox, PosPaymentLogWriter paymentLog,
            AstraEventStore astra, ObjectMapper json, PlatformTransactionManager manager) {
        this.outbox = outbox;
        this.paymentLog = paymentLog;
        this.astra = astra;
        this.json = json;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${integration.sync.interval-ms:10000}", initialDelay = 10000)
    public void scheduledDispatch() {
        if (enabled && !bootstrapping) dispatch();
    }

    public void resumeScheduledDispatch() { bootstrapping = false; }

    public Map<String, Object> dispatch() {
        if (!running.compareAndSet(false, true)) return Map.of("running", true, "sent", 0, "attempted", 0);
        int attempted = 0;
        int sent = 0;
        try {
            List<String> destinations = astraEnabled ? List.of("ASTRA", "POS_LOG") : List.of("POS_LOG");
            var ids = outbox.findReadyIds(LocalDateTime.now(), destinations,
                    PageRequest.of(0, Math.max(1, Math.min(batchSize, 100))));
            for (Long id : ids) {
                try {
                    if (Boolean.TRUE.equals(transactions.execute(status -> deliver(id)))) sent++;
                    attempted++;
                } catch (RuntimeException ex) {
                    // A database transaction failure must not abort unrelated queued deliveries.
                    log.warn("Không xử lý được outbox id={}, loại lỗi={}", id, ex.getClass().getSimpleName());
                }
            }
            return Map.of("running", false, "attempted", attempted, "sent", sent);
        } finally {
            running.set(false);
        }
    }

    private boolean deliver(Long id) {
        var entry = outbox.lockById(id).orElse(null);
        if (entry == null || !"PENDING".equals(entry.status) || entry.nextAttemptAt.isAfter(LocalDateTime.now())) return false;
        try {
            // Preserve DECIMAL amounts before projecting them to Cassandra and payment logs.
            com.fasterxml.jackson.databind.JsonNode payload = json.reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(entry.payload);
            switch (entry.destination) {
                case "ASTRA" -> astra.write(payload);
                case "POS_LOG" -> paymentLog.write(payload);
                default -> throw new IllegalArgumentException("Unsupported outbox destination: " + entry.destination);
            }
            entry.status = "SENT";
            entry.deliveredAt = LocalDateTime.now();
            entry.lastError = null;
        } catch (Exception ex) {
            entry.attempts++;
            entry.nextAttemptAt = LocalDateTime.now().plusSeconds(Math.min(300, 5L << Math.min(entry.attempts, 6)));
            // Store only the error type; connection exceptions can include credentials.
            entry.lastError = ex.getClass().getSimpleName();
            log.warn("Đồng bộ {} id={} chưa thành công, sẽ gửi lại; loại lỗi={}", entry.destination, id, entry.lastError);
        }
        outbox.save(entry);
        return "SENT".equals(entry.status);
    }
}
