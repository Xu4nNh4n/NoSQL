package com.api.supermarket.integration;

import com.api.supermarket.repository.*;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataSyncOperations {
    private final PaymentRecordRepository payments;
    private final StockMovementRepository movements;
    private final DataSyncOutboxRepository outbox;
    private final DataSyncPublisher publisher;
    private final TransactionTemplate transactions;
    private final ObjectProvider<CqlSession> sessions;
    private final DrillAnalyticsService drill;
    private final AstraAnalyticsService analytics;
    @Value("${astra.enabled:false}") private boolean cassandraEnabled;
    @Value("${astra.mode:LOCAL}") private String cassandraMode;

    public DataSyncOperations(PaymentRecordRepository payments,
            StockMovementRepository movements, DataSyncOutboxRepository outbox,
            DataSyncPublisher publisher, PlatformTransactionManager manager,
            ObjectProvider<CqlSession> sessions, DrillAnalyticsService drill, AstraAnalyticsService analytics) {
        this.payments = payments;
        this.movements = movements;
        this.outbox = outbox;
        this.publisher = publisher;
        transactions = new TransactionTemplate(manager);
        this.sessions = sessions;
        this.drill = drill;
        this.analytics = analytics;
    }

    public int requeueForLocalMigration() {
        return transactions.execute(status -> outbox.requeueForLocalMigration(LocalDateTime.now()));
    }

    // Only source records that really exist are backfilled; past order transitions are not invented.
    public void enqueueHistory() {
        for (int page = 0; ; page++) {
            int current = page;
            Integer count = transactions.execute(status -> {
                var result = payments.findAll(PageRequest.of(current, 200, Sort.by("paymentRecordId")));
                result.forEach(publisher::payment);
                return result.getNumberOfElements();
            });
            if (count < 200) break;
        }
        for (int page = 0; ; page++) {
            int current = page;
            Integer count = transactions.execute(status -> {
                var result = movements.findAll(PageRequest.of(current, 200, Sort.by("movementId")));
                result.forEach(movement -> publisher.movement(movement));
                return result.getNumberOfElements();
            });
            if (count < 200) break;
        }
    }

    public Map<String, Object> status() {
        Map<String, Long> astra = counts("ASTRA");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("astra", astra); result.put("cassandra", astra);
        result.put("astraPendingOutbox", astra.get("pending")); result.put("astraSentOutbox", astra.get("sent"));
        // Reading the outbox above confirms MySQL only; each other source is checked separately.
        result.put("mysqlConnected", true);
        result.put("posLog", counts("POS_LOG"));
        result.put("cassandraEnabled", cassandraEnabled);
        result.put("cassandraConnected", cassandraAvailable());
        result.put("cassandraMode", cassandraMode);
        boolean drillAvailable = drill.isHttpAvailable();
        result.put("drillConnected", drillAvailable);
        result.put("drillStatus", drillAvailable ? "Giao diện HTTP đang hoạt động" : "Không truy cập được Drill");
        result.put("availableExportDates", Map.of("OPERATIONS", analytics.availableExportDates(OperationalAnalyticsService.Dataset.OPERATIONS),
                "SIMULATED", analytics.availableExportDates(OperationalAnalyticsService.Dataset.SIMULATED)));
        return result;
    }

    private boolean cassandraAvailable() {
        if (!cassandraEnabled) return false;
        try {
            CqlSession session = sessions.getIfAvailable();
            return session != null && session.execute("SELECT cluster_name FROM system.local").one() != null;
        } catch (RuntimeException ex) { return false; }
    }

    private Map<String, Long> counts(String destination) {
        return Map.of("pending", outbox.countByDestinationAndStatus(destination, "PENDING"),
                "sent", outbox.countByDestinationAndStatus(destination, "SENT"));
    }
}
