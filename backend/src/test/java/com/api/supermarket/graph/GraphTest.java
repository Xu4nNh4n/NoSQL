package com.api.supermarket.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.ObjectProvider;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.graph.GraphQueryService.Suggestion;
import com.api.supermarket.graph.GraphQueryService.TracedOrder;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.inventory.BatchService;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.sync.SyncStatus;
import com.api.supermarket.sync.SyncTask;

class GraphTest {
    private static final JwtPrincipal ADMIN = new JwtPrincipal("a1", "admin", "ADMIN");

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Driver> noDriver() {
        ObjectProvider<Driver> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

    private static Document item(String pid, int qty, boolean gift) {
        return new Document("productId", pid).append("sku", "S-" + pid).append("name", pid).append("quantity", qty)
                .append("gift", gift);
    }

    @Test
    @SuppressWarnings("unchecked")
    void orderRowsAggregateQuantitiesPerProductAndBatch() {
        Document graph = new Document("orderId", "o1").append("orderCode", "HD1").append("orderedAt", Date.from(Instant.EPOCH))
                .append("customer", new Document("id", "k1").append("name", "Bình").append("phone", "0912345678"))
                .append("items", List.of(item("milk", 3, false), item("milk", 1, true), item("glass", 1, true)))
                .append("allocations", List.of(
                        new Document("batchId", "b1").append("batchCode", "L1").append("quantity", 2),
                        new Document("batchId", "b1").append("batchCode", "L1").append("quantity", 2),
                        new Document("batchId", "g1").append("batchCode", "LG").append("quantity", 1)));
        GraphRows.OrderRows rows = GraphRows.OrderRows.empty();
        GraphRows.addOrder(graph, rows);

        assertEquals(1, rows.orders().size());
        assertEquals(1, rows.customers().size());
        Map<String, Object> milk = rows.items().stream().filter(r -> r.get("productId").equals("milk")).findFirst().orElseThrow();
        assertEquals(4L, milk.get("quantity"));
        assertEquals(false, milk.get("gift"), "có dòng mua thật thì không phải quà");
        Map<String, Object> glass = rows.items().stream().filter(r -> r.get("productId").equals("glass")).findFirst().orElseThrow();
        assertEquals(true, glass.get("gift"));
        assertEquals(2, rows.allocations().size());
        assertEquals(4L, rows.allocations().get(0).get("quantity"));
    }

    @Test
    void walkInCustomerHasNoCustomerRow() {
        GraphRows.OrderRows rows = GraphRows.OrderRows.empty();
        GraphRows.addOrder(new Document("orderId", "o2").append("orderCode", "HD2").append("customer", null)
                .append("items", List.of(item("milk", 1, false))).append("allocations", List.of()), rows);
        assertTrue(rows.customers().isEmpty());
    }

    @Test
    void writerSkipsWhenNeo4jIsNotConfigured() {
        GraphWriter writer = new GraphWriter(noDriver());
        writer.apply(new SyncTask("t", InventoryEvents.ORDER_CREATED, "o1", new Document("graph", new Document("orderId", "o1")),
                SyncStatus.PENDING, 0, null, Instant.EPOCH, null, "c1"));
        assertTrue(writer.types().contains(InventoryEvents.BATCH_RECEIVED));
    }

    @Test
    void traceWithoutNeo4jIs503AndSuggestionsAreEmpty() {
        GraphQueryService q = new GraphQueryService(noDriver(), mock(ProductRepository.class));
        assertThrows(DatastoreUnavailableException.class, () -> q.trace("L1"));
        assertFalse(q.alsoBought("p1", 5).available());
    }

    @Test
    void suggestionsKeepOnlyActiveProductsUpToLimit() {
        ProductRepository products = mock(ProductRepository.class);
        Product a = new Product();
        a.setId("a");
        a.setActive(true);
        a.setName("A");
        Product b = new Product();
        b.setId("b");
        b.setActive(false);
        when(products.findById("a")).thenReturn(Optional.of(a));
        when(products.findById("b")).thenReturn(Optional.of(b));
        when(products.findById("c")).thenReturn(Optional.empty());
        GraphQueryService q = new GraphQueryService(noDriver(), products);
        List<Suggestion> res = q.onlyActive(List.of(new Suggestion("b", null, "B", 9), new Suggestion("c", null, "C", 8),
                new Suggestion("a", null, "A", 7)), 5);
        assertEquals(List.of(new Suggestion("a", null, "A", 7)), res);
    }

    @Test
    void traceGroupsContactsAndSkipsWalkIns() {
        BatchRepository batches = mock(BatchRepository.class);
        GraphQueryService graph = mock(GraphQueryService.class);
        Batch b = new Batch();
        b.setId("b1");
        b.setBatchCode("L261101-0003");
        b.setReceivedQty(100);
        b.setRemainingQty(40);
        b.setExpiryDate(LocalDate.of(2026, 11, 20));
        when(batches.findByCode("L261101-0003")).thenReturn(Optional.of(b));
        when(graph.trace("L261101-0003")).thenReturn(List.of(
                new TracedOrder("HD1", Instant.EPOCH, 3, "k1", "Bình", "0912"),
                new TracedOrder("HD2", Instant.EPOCH, 2, null, null, null),
                new TracedOrder("HD3", Instant.EPOCH, 5, "k1", "Bình", "0912")));
        RecallService.TraceResult r = new RecallService(batches, mock(BatchService.class), graph).trace(" l261101-0003 ");
        assertEquals(10, r.soldQty());
        assertEquals(1, r.contacts().size());
        assertEquals(8, r.contacts().get(0).quantity());
    }

    @Test
    void recallAdjustsAllRemainingStock() {
        BatchRepository batches = mock(BatchRepository.class);
        BatchService batchService = mock(BatchService.class);
        GraphQueryService graph = mock(GraphQueryService.class);
        Batch b = new Batch();
        b.setId("b1");
        b.setBatchCode("L1");
        b.setRemainingQty(40);
        when(batches.findByCode("L1")).thenReturn(Optional.of(b));
        when(graph.trace("L1")).thenReturn(List.of());
        RecallService service = new RecallService(batches, batchService, graph);

        service.recall("L1", "NCC báo lỗi bao bì", ADMIN);
        verify(batchService).adjust("b1", 40, "Thu hồi: NCC báo lỗi bao bì", ADMIN);

        b.setRemainingQty(0);
        service.recall("L1", "lần 2", ADMIN);
        verify(batchService, times(1)).adjust(anyString(), anyInt(), anyString(), any());
        assertThrows(BadRequestException.class, () -> service.recall("L1", " ", ADMIN));
    }
}
