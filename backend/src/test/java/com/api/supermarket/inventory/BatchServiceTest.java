package com.api.supermarket.inventory;

import static com.api.supermarket.inventory.InventoryTestSupport.KEEPER;
import static com.api.supermarket.inventory.InventoryTestSupport.TIME;
import static com.api.supermarket.inventory.InventoryTestSupport.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.common.RetryableConflictException;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.inventory.BatchService.ReceiveCommand;
import com.api.supermarket.sync.SyncQueue;

class BatchServiceTest {
    private BatchRepository batches;
    private ProductRepository products;
    private SupplierService suppliers;
    private SyncQueue sync;
    private ProductCache cache;
    private BatchService service;

    @BeforeEach
    void setUp() {
        batches = mock(BatchRepository.class);
        products = mock(ProductRepository.class);
        suppliers = mock(SupplierService.class);
        sync = mock(SyncQueue.class);
        cache = mock(ProductCache.class);
        CodeGenerator codes = mock(CodeGenerator.class);
        when(codes.next(DocumentType.BATCH)).thenReturn("L261107-0001");
        service = new BatchService(batches, products, suppliers, codes, sync,
                new MongoTx(TransactionOperations.withoutTransaction()), cache, TIME);

        when(products.findById("p1")).thenReturn(Optional.of(InventoryTestSupport.product("p1", true)));
        when(products.findById("p2")).thenReturn(Optional.of(InventoryTestSupport.product("p2", false)));
        when(suppliers.loadActive("s1")).thenReturn(InventoryTestSupport.supplier());
        when(batches.insert(any())).thenAnswer(inv -> {
            Batch b = inv.getArgument(0);
            b.setId("b1");
            return b;
        });
    }

    private static ReceiveCommand cmd(String productId, int qty, java.time.LocalDate mfg, java.time.LocalDate expiry) {
        return new ReceiveCommand(productId, null, qty, new BigDecimal("24000"), mfg, expiry, null, null);
    }

    @Test
    void receiveLooseCreatesBatchAddsStockAndQueuesLedgerEvent() {
        Batch b = service.receiveLoose(cmd("p1", 100, TODAY.minusDays(3), TODAY.plusDays(10)), KEEPER);

        assertEquals("L261107-0001", b.getBatchCode());
        assertEquals(100, b.getRemainingQty());
        assertEquals(TODAY.plusDays(10), b.getExpirySortKey());
        assertEquals("Vinamilk", b.getSupplierName());
        verify(products).incStock("p1", 100, 0);
        verify(cache).evict();

        ArgumentCaptor<Document> payload = ArgumentCaptor.forClass(Document.class);
        verify(sync).enqueue(eq(InventoryEvents.BATCH_RECEIVED), eq("b1"), payload.capture(), eq("w1"));
        List<StockMovement> movements = InventoryEvents.movements(payload.getValue());
        assertEquals(1, movements.size());
        assertEquals(StockMovement.RECEIPT, movements.get(0).type());
        assertEquals(100, movements.get(0).quantity());
        assertEquals(StockMovement.idFor("RECEIPT", "b1"), movements.get(0).id());
        assertEquals(1, payload.getValue().getList("batches", Document.class).size());
    }

    @Test
    void batchWithoutExpirySortsLast() {
        Batch b = service.receiveLoose(cmd("p1", 5, null, null), KEEPER);
        assertEquals(Batch.NO_EXPIRY, b.getExpirySortKey());
    }

    @Test
    void dateRules() {
        assertThrows(BadRequestException.class, () -> service.receiveLoose(cmd("p1", 5, TODAY.plusDays(1), null), KEEPER));
        assertThrows(BadRequestException.class, () -> service.receiveLoose(cmd("p1", 5, null, TODAY.minusDays(1)), KEEPER));
        assertThrows(BadRequestException.class,
                () -> service.receiveLoose(cmd("p1", 5, TODAY.minusDays(1), TODAY.minusDays(2)), KEEPER));
        // hạn dùng đúng hôm nay vẫn nhận được
        service.receiveLoose(cmd("p1", 5, TODAY, TODAY), KEEPER);
    }

    @Test
    void inactiveProductOrZeroQuantityRejected() {
        assertThrows(BadRequestException.class, () -> service.receiveLoose(cmd("p2", 5, null, null), KEEPER));
        assertThrows(BadRequestException.class, () -> service.receiveLoose(cmd("p1", 0, null, null), KEEPER));
        verify(batches, never()).insert(any());
    }

    @Test
    void adjustDecrementsBatchAndProductAndRecordsNegativeMovement() {
        Batch b = InventoryTestSupport.batch("b1", "p1", 10, TODAY.plusDays(5));
        when(batches.findById("b1")).thenReturn(Optional.of(b));
        when(batches.decrementIfEnough("b1", 3)).thenReturn(true);

        service.adjust("b1", 3, " Vỡ hộp ", KEEPER);

        verify(products).incStock("p1", -3, 0);
        verify(batches).pushAdjustment(eq("b1"), any());
        ArgumentCaptor<Document> payload = ArgumentCaptor.forClass(Document.class);
        verify(sync).enqueue(eq(InventoryEvents.STOCK_ADJUSTED), eq("b1"), payload.capture(), eq("w1"));
        StockMovement m = InventoryEvents.movements(payload.getValue()).get(0);
        assertEquals(-3, m.quantity());
        assertEquals("Lô L-b1: Vỡ hộp", m.note());
    }

    @Test
    void adjustRules() {
        Batch b = InventoryTestSupport.batch("b1", "p1", 2, TODAY.plusDays(5));
        when(batches.findById("b1")).thenReturn(Optional.of(b));
        assertThrows(BadRequestException.class, () -> service.adjust("b1", 3, "hỏng", KEEPER));
        assertThrows(BadRequestException.class, () -> service.adjust("b1", 1, " ", KEEPER));
        verify(products, never()).incStock(anyString(), anyInt(), anyInt());
    }

    @Test
    void adjustRetriesWhenBatchChangedConcurrently() {
        Batch b = InventoryTestSupport.batch("b1", "p1", 10, TODAY.plusDays(5));
        when(batches.findById("b1")).thenReturn(Optional.of(b));
        when(batches.decrementIfEnough("b1", 3)).thenReturn(false);
        assertThrows(RetryableConflictException.class, () -> service.adjust("b1", 3, "hỏng", KEEPER));
        verify(batches, times(3)).decrementIfEnough("b1", 3);
    }
}
