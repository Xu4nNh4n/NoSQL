package com.api.supermarket.service.Impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.api.supermarket.domain.BatchAllocationData;
import com.api.supermarket.entity.*;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.integration.DataSyncPublisher;
import com.api.supermarket.repository.*;
import com.api.supermarket.service.InventoryBatchService;

class InventoryBatchServiceTest {
    private final InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
    private final InventoryBatchService service = new InventoryBatchService(batches, mock(ProductRepository.class),
            mock(StockMovementRepository.class), mock(DataSyncPublisher.class), mock(SupplierRepository.class));
    private InventoryBatch batch(long id) {
        InventoryBatch b = new InventoryBatch();
        b.id = id; b.productId = 1L; b.remaining = 10; b.status = "ACTIVE";
        b.expiryDate = LocalDate.now().plusDays(id); b.receivedAt = LocalDateTime.now();
        return b;
    }
    private SalesOrderItem item() {
        Product p = new Product(); p.setProductId(1L);
        SalesOrderItem i = new SalesOrderItem(); i.setOrderItemId(10L); i.setProduct(p); i.setQuantity(6);
        i.setBatchAllocations(List.of(new BatchAllocationData(1L, 2, 0, "CONSUMED"),
                new BatchAllocationData(2L, 4, 0, "CONSUMED")));
        return i;
    }
    @Test void partialReturnPreservesUnprocessedLots() {
        var a = batch(1); var b = batch(2); var i = item();
        when(batches.lockProductBatches(1L)).thenReturn(List.of(a, b));
        var result = service.receiveReturn(i, 1, true, 1L, "Unopened");
        assertEquals(2, i.getBatchAllocations().size());
        assertEquals(1, i.getBatchAllocations().get(0).returnedQuantity());
        assertEquals(0, i.getBatchAllocations().get(1).returnedQuantity());
        assertEquals(11, a.remaining); assertEquals(10, b.remaining);
        assertEquals(1L, result.get(0).batchId());
    }
    @Test void fullReturnSplitsActualLots() {
        var a = batch(1); var b = batch(2); var i = item();
        when(batches.lockProductBatches(1L)).thenReturn(List.of(a, b));
        var result = service.receiveReturn(i, 6, true, null, "Unopened");
        assertEquals(List.of(2, 4), result.stream().map(v -> v.quantity()).toList());
        assertEquals(12, a.remaining); assertEquals(14, b.remaining);
    }
    @Test void partialMultiLotReturnNeedsActualLot() {
        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch(1), batch(2)));
        assertThrows(BadRequestException.class, () -> service.receiveReturn(item(), 1, true, null, "Unopened"));
    }
    @Test void expiredLotRejectsRestockButAllowsDamagedReturn() {
        var a = batch(1); a.expiryDate = LocalDate.now().minusDays(1);
        when(batches.lockProductBatches(1L)).thenReturn(List.of(a, batch(2)));
        assertThrows(BadRequestException.class, () -> service.receiveReturn(item(), 1, true, 1L, "Expired"));
        service.receiveReturn(item(), 1, false, 1L, "Expired");
        assertEquals(10, a.remaining); assertEquals(1, a.damaged);
    }
    @Test void allocationPersistsFefoInJson() {
        var a = batch(1); var b = batch(2); a.remaining = 2; var i = item();
        when(batches.lockProductBatches(1L)).thenReturn(List.of(b, a));
        service.allocate(i);
        assertEquals(List.of(1L, 2L), i.getBatchAllocations().stream().map(BatchAllocationData::batchId).toList());
        assertEquals(0, a.remaining); assertEquals(6, b.remaining);
    }
}
