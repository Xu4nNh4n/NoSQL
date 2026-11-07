package com.api.supermarket.inventory;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.api.supermarket.sync.SyncHandler;
import com.api.supermarket.sync.SyncTask;

/** Ghi phần {@code movements} của sự kiện kho, bán, trả sang thẻ kho Cassandra. */
@Component
public class StockLedgerSyncHandler implements SyncHandler {
    private final StockLedger ledger;

    public StockLedgerSyncHandler(StockLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public Set<String> types() {
        return Set.of(InventoryEvents.BATCH_RECEIVED, InventoryEvents.STOCK_ADJUSTED, InventoryEvents.ORDER_CREATED,
                InventoryEvents.RETURN_CREATED);
    }

    @Override
    public void apply(SyncTask task) {
        ledger.write(InventoryEvents.movements(task.getPayload()));
    }
}
