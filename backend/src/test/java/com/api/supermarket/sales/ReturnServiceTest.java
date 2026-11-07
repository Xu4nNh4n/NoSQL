package com.api.supermarket.sales;

import static com.api.supermarket.sales.SalesTestSupport.CASHIER;
import static com.api.supermarket.sales.SalesTestSupport.TIME;
import static com.api.supermarket.sales.SalesTestSupport.TODAY;
import static com.api.supermarket.sales.SalesTestSupport.paidOrder;
import static com.api.supermarket.sales.SalesTestSupport.vnd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.CodeGenerator;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.inventory.StockMovement;
import com.api.supermarket.sales.ReturnService.ReturnItemRequest;
import com.api.supermarket.sales.ReturnService.ReturnRequest;
import com.api.supermarket.sync.SyncQueue;

class ReturnServiceTest {
    private OrderRepository orders;
    private BatchRepository batches;
    private ProductRepository products;
    private SyncQueue sync;
    private ReturnService service;
    private SalesOrder order;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        batches = mock(BatchRepository.class);
        products = mock(ProductRepository.class);
        sync = mock(SyncQueue.class);
        CodeGenerator codes = mock(CodeGenerator.class);
        when(codes.next(DocumentType.RETURN)).thenReturn("PT261107-0001");
        service = new ReturnService(orders, batches, products, codes, sync,
                new MongoTx(TransactionOperations.withoutTransaction()), mock(ProductCache.class), TIME, 7);
        order = paidOrder();
        when(orders.findById("o1")).thenReturn(Optional.of(order));
        when(orders.findReturnByRequestKey(anyString())).thenReturn(Optional.empty());
        when(orders.insertReturn(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ReturnRequest req(String key, int qty, Boolean restock) {
        return new ReturnRequest(key, List.of(new ReturnItemRequest(1, qty, restock, "Khách đổi ý")),
                SalesOrder.Method.CASH, null);
    }

    @Test
    void partialReturnTakesLatestExpiryBatchFirstAndRefundsShare() {
        SalesReturn r = service.create("o1", req("r1", 2, true), CASHIER);

        assertEquals(vnd(40_000), r.getRefundAmount());
        assertEquals(List.of(new SalesReturn.BatchPart("far", "L-far", 2)), r.getItems().get(0).getBatches());
        verify(batches).returnToBatch("far", 2, true);
        verify(products).incStock("milk", 2, 0);
        assertEquals(SalesOrder.Status.PARTIALLY_RETURNED, order.getStatus());
        assertEquals(vnd(40_000), order.getRefundedTotal());
        SalesOrder.Payment refund = order.getPayments().get(order.getPayments().size() - 1);
        assertEquals(SalesOrder.Payment.Type.REFUND, refund.getType());
        assertEquals("PT261107-0001", refund.getReturnCode());

        ArgumentCaptor<Document> payload = ArgumentCaptor.forClass(Document.class);
        verify(sync).enqueue(eq(InventoryEvents.RETURN_CREATED), anyString(), payload.capture(), eq("c1"));
        StockMovement m = InventoryEvents.movements(payload.getValue()).get(0);
        assertEquals(StockMovement.RETURN, m.type());
        assertEquals(2, m.quantity());
        assertEquals("PT261107-0001", m.refCode());
    }

    @Test
    void fullReturnInTwoStepsRefundsExactlyNetAndMarksReturned() {
        service.create("o1", req("r1", 2, true), CASHIER);
        SalesReturn second = service.create("o1", req("r2", 1, false), CASHIER);
        assertEquals(vnd(20_000), second.getRefundAmount());
        assertEquals(vnd(60_000), order.getRefundedTotal());
        assertEquals(SalesOrder.Status.RETURNED, order.getStatus());
        verify(batches).returnToBatch("near", 1, false);
        verify(products).incStock("milk", 0, 1);
    }

    @Test
    void cannotReturnMoreThanRemaining() {
        assertThrows(BadRequestException.class, () -> service.create("o1", req("r1", 4, true), CASHIER));
        verify(batches, never()).returnToBatch(anyString(), anyInt(), anyBoolean());
    }

    @Test
    void expiredBatchMustBeReturnedAsDamaged() {
        // cả hai lô đều đã hết hạn (lô "far" vẫn có hạn xa hơn nên được lấy trước)
        order.getItems().get(0).getAllocations().get(0).setExpiryDate(TODAY.minusDays(3));
        order.getItems().get(0).getAllocations().get(1).setExpiryDate(TODAY.minusDays(1));
        assertThrows(BadRequestException.class, () -> service.create("o1", req("r1", 1, true), CASHIER));
        service.create("o1", req("r2", 1, false), CASHIER);
    }

    @Test
    void returnWindowIsSevenDays() {
        order.setOrderDay(TODAY.minusDays(8));
        assertThrows(BadRequestException.class, () -> service.create("o1", req("r1", 1, true), CASHIER));
        order.setOrderDay(TODAY.minusDays(7));
        service.create("o1", req("r2", 1, true), CASHIER);
    }

    @Test
    void bankRefundNeedsReference() {
        assertThrows(BadRequestException.class, () -> service.create("o1", new ReturnRequest("r1",
                List.of(new ReturnItemRequest(1, 1, true, "x")), SalesOrder.Method.BANK_TRANSFER, null), CASHIER));
    }
}
