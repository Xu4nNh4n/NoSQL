package com.api.supermarket.inventory;

import static com.api.supermarket.inventory.InventoryTestSupport.NOW;
import static com.api.supermarket.inventory.InventoryTestSupport.TIME;
import static com.api.supermarket.inventory.InventoryTestSupport.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.RedisSafe;
import com.api.supermarket.sync.SyncStatus;
import com.api.supermarket.sync.SyncTask;

import tools.jackson.databind.json.JsonMapper;

class LedgerAndLowStockTest {

    @Test
    void movementSurvivesPayloadRoundTrip() {
        StockMovement m = new StockMovement(StockMovement.idFor("SALE", "o1", "1", "b1"), "p1", NOW, StockMovement.SALE,
                -3, "L1", "ORDER", "HD261107-0001", null, "thungan01");
        assertEquals(m, StockMovement.fromDocument(m.toDocument()));
        assertEquals(StockMovement.idFor("SALE", "o1", "1", "b1"), m.id(), "id phải xác định từ nội dung");
    }

    @Test
    void monthBucketUsesShopTimezone() {
        // 2026-10-31 18:30 UTC = 2026-11-01 01:30 giờ Việt Nam
        assertEquals("2026-11", StockLedger.month(java.time.Instant.parse("2026-10-31T18:30:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void handlerWritesMovementsFromPayload() {
        StockLedger ledger = mock(StockLedger.class);
        StockMovement m = new StockMovement(StockMovement.idFor("RECEIPT", "b1"), "p1", NOW, StockMovement.RECEIPT, 5,
                "L1", "BATCH", "L1", null, "thukho01");
        SyncTask task = new SyncTask("t1", InventoryEvents.BATCH_RECEIVED, "b1",
                InventoryEvents.payload(List.of(m), List.of()), SyncStatus.PENDING, 0, null, NOW, null, "w1");
        StockLedgerSyncHandler handler = new StockLedgerSyncHandler(ledger);
        assertTrue(handler.types().contains(InventoryEvents.ORDER_CREATED));
        handler.apply(task);
        verify(ledger).write(List.of(m));
    }

    @Test
    void lowStockUsesUnexpiredAvailableQuantity() {
        BatchRepository batches = mock(BatchRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        Product milk = InventoryTestSupport.product("p1", true);
        milk.setReorderPoint(20);
        milk.setStockQuantity(40); // 40 trong kho nhưng 30 đã hết hạn
        Product rice = InventoryTestSupport.product("p2", true);
        rice.setReorderPoint(5);
        when(products.findAllActive()).thenReturn(List.of(milk, rice));
        when(batches.availableByProduct(TODAY)).thenReturn(Map.of("p1", 10, "p2", 50));

        LowStockService service = new LowStockService(batches, products, mock(RedisSafe.class),
                JsonMapper.builder().build(), TIME);
        List<LowStockService.LowStockItem> items = service.compute();
        assertEquals(1, items.size());
        assertEquals("p1", items.get(0).productId());
        assertEquals(10, items.get(0).available());
    }
}
