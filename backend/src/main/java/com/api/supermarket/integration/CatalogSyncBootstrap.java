package com.api.supermarket.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "integration.sync.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSyncBootstrap {
    private static final Logger log = LoggerFactory.getLogger(CatalogSyncBootstrap.class);
    private final DataSyncOperations operations;

    public CatalogSyncBootstrap(DataSyncOperations operations) { this.operations = operations; }

    @EventListener(ApplicationReadyEvent.class)
    public void enqueueExistingHistory() {
        operations.enqueueHistory();
        log.info("Đã đưa lịch sử kho và biên nhận hiện có vào hàng đợi Astra");
    }
}
