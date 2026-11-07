package com.api.supermarket.sales;

import static com.api.supermarket.sales.SalesTestSupport.CASHIER;
import static com.api.supermarket.sales.SalesTestSupport.TIME;
import static com.api.supermarket.sales.SalesTestSupport.TODAY;
import static com.api.supermarket.sales.SalesTestSupport.cart;
import static com.api.supermarket.sales.SalesTestSupport.line;
import static com.api.supermarket.sales.SalesTestSupport.vnd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.common.DocumentType;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.inventory.FefoAllocator;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.inventory.StockMovement;
import com.api.supermarket.sales.OrderService.CreateOrderRequest;
import com.api.supermarket.sales.QuoteService.QuoteItem;
import com.api.supermarket.sync.SyncQueue;

class OrderServiceTest {
    private OrderRepository orders;
    private QuoteService quotes;
    private FefoAllocator fefo;
    private ProductRepository products;
    private SyncQueue sync;
    private TopSalesService topSales;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        quotes = mock(QuoteService.class);
        fefo = mock(FefoAllocator.class);
        products = mock(ProductRepository.class);
        sync = mock(SyncQueue.class);
        topSales = mock(TopSalesService.class);
        CodeGenerator codes = mock(CodeGenerator.class);
        when(codes.next(DocumentType.ORDER)).thenReturn("HD261107-0042");
        service = new OrderService(orders, quotes, fefo, products, codes, sync,
                new MongoTx(TransactionOperations.withoutTransaction()), mock(ProductCache.class), topSales, TIME);
        when(orders.findByRequestKey(anyString())).thenReturn(Optional.empty());
        when(orders.insert(any())).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            o.setId("o1");
            return o;
        });
        // beer x2 = 40k, gift glass x1 trị giá 10k: tiền gói 40k chia 32k / 8k
        when(quotes.quote(any())).thenReturn(cart("H1", line(1, "beer", 2, 20_000, 32_000, false),
                line(2, "glass", 1, 10_000, 8_000, true)));
        when(fefo.allocate(eq("beer"), anyString(), eq(2))).thenReturn(List.of(
                new FefoAllocator.Allocation("b1", "L1", TODAY.plusDays(3), 1),
                new FefoAllocator.Allocation("b2", "L2", TODAY.plusDays(9), 1)));
        when(fefo.allocate(eq("glass"), anyString(), eq(1))).thenReturn(List.of(
                new FefoAllocator.Allocation("g1", "LG", null, 1)));
    }

    private static CreateOrderRequest req(String key, String hash, SalesOrder.Method method, String ref) {
        return new CreateOrderRequest(key, List.of(new QuoteItem("beer", 2)), method, ref, hash);
    }

    @Test
    void createsPaidOrderWithAllocationsForEveryLineIncludingGift() {
        SalesOrder o = service.create(req("k1", "H1", SalesOrder.Method.CASH, null), CASHIER);

        assertEquals("HD261107-0042", o.getOrderCode());
        assertEquals(SalesOrder.Status.PAID, o.getStatus());
        assertEquals(vnd(40_000), o.getTotal());
        assertEquals(2, o.getItems().get(0).getAllocations().size());
        assertTrue(o.getItems().get(1).isGift());
        verify(products).incStock("beer", -2, 0);
        verify(products).incStock("glass", -1, 0);
        SalesOrder.Payment pay = o.getPayments().get(0);
        assertEquals(vnd(40_000), pay.getAmount());
        assertEquals("POS-HD261107-0042", pay.getReference());
        verify(topSales).record(o);

        ArgumentCaptor<Document> payload = ArgumentCaptor.forClass(Document.class);
        verify(sync).enqueue(eq(InventoryEvents.ORDER_CREATED), eq("o1"), payload.capture(), eq("c1"));
        List<StockMovement> movements = InventoryEvents.movements(payload.getValue());
        assertEquals(3, movements.size(), "mỗi lô của mỗi dòng một dòng thẻ kho SALE");
        assertTrue(movements.stream().allMatch(m -> m.quantity() < 0 && m.type().equals("SALE")));
        assertEquals(1, SalesEvents.payments(payload.getValue()).size());
        assertEquals(3, payload.getValue().get("graph", Document.class).getList("allocations", Document.class).size());
    }

    @Test
    void priceChangedIsConflictAndNothingIsTouched() {
        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.create(req("k1", "OLD", SalesOrder.Method.CASH, null), CASHIER));
        assertEquals("PRICE_CHANGED", ex.code());
        verify(fefo, never()).allocate(anyString(), anyString(), anyInt());
        verify(orders, never()).insert(any());
    }

    @Test
    void bankTransferNeedsReference() {
        assertThrows(BadRequestException.class,
                () -> service.create(req("k1", "H1", SalesOrder.Method.BANK_TRANSFER, " "), CASHIER));
        SalesOrder o = service.create(req("k2", "H1", SalesOrder.Method.BANK_TRANSFER, "FT2611070001"), CASHIER);
        assertEquals("FT2611070001", o.getPayments().get(0).getReference());
    }

    @Test
    void resubmittingSameRequestReturnsExistingOrder() {
        CreateOrderRequest r = req("k1", "H1", SalesOrder.Method.CASH, null);
        SalesOrder existing = new SalesOrder();
        existing.setRequestFingerprint(OrderService.fingerprint(r, null));
        when(orders.findByRequestKey("k1")).thenReturn(Optional.of(existing));
        assertSame(existing, service.create(r, CASHIER));
        verify(orders, never()).insert(any());
    }

    @Test
    void reusingKeyForDifferentCartIsConflict() {
        SalesOrder existing = new SalesOrder();
        existing.setRequestFingerprint("other");
        when(orders.findByRequestKey("k1")).thenReturn(Optional.of(existing));
        assertThrows(ConflictException.class, () -> service.create(req("k1", "H1", SalesOrder.Method.CASH, null), CASHIER));
    }

    @Test
    void fingerprintIgnoresLineOrderAndSplitScans() {
        CreateOrderRequest a = new CreateOrderRequest("k",
                List.of(new QuoteItem("a", 1), new QuoteItem("b", 2), new QuoteItem("a", 1)), SalesOrder.Method.CASH, null, "h");
        CreateOrderRequest b = new CreateOrderRequest("k",
                List.of(new QuoteItem("b", 2), new QuoteItem("a", 2)), SalesOrder.Method.CASH, null, "h");
        assertEquals(OrderService.fingerprint(a, null), OrderService.fingerprint(b, null));
    }
}
