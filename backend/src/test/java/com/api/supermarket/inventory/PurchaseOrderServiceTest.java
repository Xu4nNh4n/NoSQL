package com.api.supermarket.inventory;

import static com.api.supermarket.inventory.InventoryTestSupport.KEEPER;
import static com.api.supermarket.inventory.InventoryTestSupport.TIME;
import static com.api.supermarket.inventory.InventoryTestSupport.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.inventory.BatchService.Received;
import com.api.supermarket.inventory.PurchaseOrderService.LineRequest;
import com.api.supermarket.inventory.PurchaseOrderService.PurchaseOrderRequest;
import com.api.supermarket.inventory.PurchaseOrderService.ReceiveLine;
import com.api.supermarket.inventory.PurchaseOrderService.ReceiveRequest;
import com.api.supermarket.sync.SyncQueue;

class PurchaseOrderServiceTest {
    private PurchaseOrderRepository orders;
    private BatchService batches;
    private SyncQueue sync;
    private PurchaseOrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(PurchaseOrderRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        SupplierService suppliers = mock(SupplierService.class);
        batches = mock(BatchService.class);
        sync = mock(SyncQueue.class);
        CodeGenerator codes = mock(CodeGenerator.class);
        when(codes.next(DocumentType.PURCHASE_ORDER)).thenReturn("PN261107-0001");
        service = new PurchaseOrderService(orders, products, suppliers, batches, codes, sync,
                new MongoTx(TransactionOperations.withoutTransaction()), mock(ProductCache.class), TIME);

        when(products.findById("p1")).thenReturn(Optional.of(InventoryTestSupport.product("p1", true)));
        when(products.findById("p2")).thenReturn(Optional.of(InventoryTestSupport.product("p2", true)));
        when(suppliers.loadActive("s1")).thenReturn(InventoryTestSupport.supplier());
        when(orders.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PurchaseOrder draft() {
        PurchaseOrder po = service.create(new PurchaseOrderRequest("s1", "đợt 1", List.of(
                new LineRequest("p1", 100, new BigDecimal("24000")),
                new LineRequest("p2", 50, new BigDecimal("10000"))), null), KEEPER);
        po.setId("po1");
        po.setVersion(0L);
        when(orders.findById("po1")).thenReturn(Optional.of(po));
        return po;
    }

    @Test
    void createNumbersLinesAndCopiesProductInfo() {
        PurchaseOrder po = draft();
        assertEquals("PN261107-0001", po.getPoCode());
        assertEquals(PurchaseOrder.Status.DRAFT, po.getStatus());
        assertEquals(2, po.getLines().size());
        assertEquals(2, po.getLines().get(1).getLineNo());
        assertEquals("SKU-p1", po.getLines().get(0).getSku());
        assertEquals("Vinamilk", po.getSupplierName());
    }

    @Test
    void duplicateProductLinesRejected() {
        assertThrows(BadRequestException.class, () -> service.create(new PurchaseOrderRequest("s1", null, List.of(
                new LineRequest("p1", 1, BigDecimal.ONE), new LineRequest("p1", 2, BigDecimal.ONE)), null), KEEPER));
    }

    @Test
    void receiveCreatesBatchesForReceivedLinesOnlyAndQueuesOneEvent() {
        draft();
        when(batches.receiveInTx(any(), eq(KEEPER))).thenAnswer(inv -> {
            BatchService.ReceiveCommand c = inv.getArgument(0);
            Batch b = InventoryTestSupport.batch("b-" + c.productId(), c.productId(), c.quantity(), c.expiryDate());
            return new Received(b, new StockMovement(StockMovement.idFor("RECEIPT", b.getId()), c.productId(),
                    InventoryTestSupport.NOW, StockMovement.RECEIPT, c.quantity(), b.getBatchCode(), "PURCHASE_ORDER",
                    c.purchaseOrderCode(), null, "thukho01"));
        });

        PurchaseOrder po = service.receive("po1", new ReceiveRequest(List.of(
                new ReceiveLine(1, 98, TODAY.minusDays(2), TODAY.plusDays(10)),
                new ReceiveLine(2, 0, null, null))), KEEPER);

        assertEquals(PurchaseOrder.Status.RECEIVED, po.getStatus());
        assertEquals("L-b-p1", po.getLines().get(0).getBatchCode());
        assertEquals(98, po.getLines().get(0).getReceivedQty());
        verify(batches, times(1)).receiveInTx(any(), any());
        ArgumentCaptor<Document> payload = ArgumentCaptor.forClass(Document.class);
        verify(sync).enqueue(eq(InventoryEvents.BATCH_RECEIVED), eq("po1"), payload.capture(), eq("w1"));
        assertEquals(1, InventoryEvents.movements(payload.getValue()).size());
    }

    @Test
    void receivingTwiceReturnsExistingResult() {
        PurchaseOrder po = draft();
        po.setStatus(PurchaseOrder.Status.RECEIVED);
        assertSame(po, service.receive("po1", new ReceiveRequest(List.of(new ReceiveLine(1, 5, null, null))), KEEPER));
        verify(batches, never()).receiveInTx(any(), any());
        verify(sync, never()).enqueue(anyString(), anyString(), any(), anyString());
    }

    @Test
    void receiveValidation() {
        draft();
        assertThrows(BadRequestException.class,
                () -> service.receive("po1", new ReceiveRequest(List.of(new ReceiveLine(9, 5, null, null))), KEEPER));
        assertThrows(BadRequestException.class,
                () -> service.receive("po1", new ReceiveRequest(List.of(new ReceiveLine(1, 0, null, null))), KEEPER));
    }

    @Test
    void concurrentReceiveIsRetriedAndThenSeesReceived() {
        PurchaseOrder po = draft();
        when(batches.receiveInTx(any(), any())).thenReturn(new Received(
                InventoryTestSupport.batch("b1", "p1", 5, null),
                new StockMovement(StockMovement.idFor("RECEIPT", "b1"), "p1", InventoryTestSupport.NOW,
                        StockMovement.RECEIPT, 5, "L-b1", "PURCHASE_ORDER", "PN", null, "x")));
        PurchaseOrder alreadyReceived = new PurchaseOrder();
        alreadyReceived.setStatus(PurchaseOrder.Status.RECEIVED);
        when(orders.save(po)).thenThrow(new OptimisticLockingFailureException("version"));
        when(orders.findById("po1")).thenReturn(Optional.of(po), Optional.of(alreadyReceived));

        assertSame(alreadyReceived,
                service.receive("po1", new ReceiveRequest(List.of(new ReceiveLine(1, 5, null, null))), KEEPER));
    }

    @Test
    void onlyDraftsCanBeEditedOrDeleted() {
        PurchaseOrder po = draft();
        po.setStatus(PurchaseOrder.Status.RECEIVED);
        assertThrows(BadRequestException.class, () -> service.delete("po1"));
        assertThrows(BadRequestException.class, () -> service.update("po1", new PurchaseOrderRequest("s1", null,
                List.of(new LineRequest("p1", 1, BigDecimal.ONE)), 0L)));
    }
}
