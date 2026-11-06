package com.api.supermarket.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit, repeatable migration to local sources; never runs on a normal startup. */
@Component
@ConditionalOnProperty(name = "integration.local-bootstrap", havingValue = "true")
public class LocalSourceBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalSourceBootstrap.class);
    private final DataSyncOperations operations;
    private final DataSyncDispatcher dispatcher;
    private final AstraAnalyticsService analytics;
    @Value("${astra.mode:LOCAL}") private String mode;
    @Value("${astra.enabled:false}") private boolean enabled;

    public LocalSourceBootstrap(DataSyncOperations operations, DataSyncDispatcher dispatcher,
            AstraAnalyticsService analytics) {
        this.operations = operations; this.dispatcher = dispatcher; this.analytics = analytics;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || !"LOCAL".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Bootstrap chỉ chạy với Cassandra local đã bật");
        }
        try {
            operations.enqueueHistory();
            int replay = operations.requeueForLocalMigration();
            log.info("LOCAL_BOOTSTRAP: replay={}; giữ nguyên payload/mốc thời gian sự kiện gốc", replay);
            int delivered = 0;
            while (true) {
                var batch = dispatcher.dispatch();
                int sent = ((Number) batch.get("sent")).intValue();
                delivered += sent;
                if (((Number) batch.get("attempted")).intValue() == 0 || sent == 0) break;
            }
            var operationsExport = analytics.exportAvailableDays(OperationalAnalyticsService.Dataset.OPERATIONS);
            var simulationExport = analytics.exportAvailableDays(OperationalAnalyticsService.Dataset.SIMULATED);
            log.info("LOCAL_BOOTSTRAP_DONE: delivered={}, operationsExport={}, simulationExport={}",
                    delivered, operationsExport, simulationExport);
        } finally {
            dispatcher.resumeScheduledDispatch();
        }
    }
}
