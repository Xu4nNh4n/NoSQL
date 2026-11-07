package com.api.supermarket.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;

import com.api.supermarket.support.CloudTest;

/** Ghi lặp không tạo trùng và truy vết đúng trên Neo4j Aura thật. Dùng id có tiền tố riêng rồi xóa. */
@CloudTest
class GraphCloudTest {
    private static final String P = "cloudtest-";

    @Autowired
    GraphWriter writer;
    @Autowired
    GraphQueryService query;
    @Autowired
    Driver driver;

    @AfterEach
    void cleanup() {
        try (var s = driver.session()) {
            s.run("MATCH (n) WHERE n.id STARTS WITH $p DETACH DELETE n", Map.of("p", P)).consume();
        }
    }

    @Test
    void mergeTwiceThenTraceAndSuggest() {
        writer.writeBatches(driver, List.of(Map.of("batchId", P + "b1", "batchCode", P + "L1", "productId", P + "milk",
                "sku", "M", "productName", "Milk", "supplierId", P + "s1", "supplierName", "NCC")));
        for (String orderId : List.of(P + "o1", P + "o2")) {
            GraphRows.OrderRows rows = GraphRows.OrderRows.empty();
            Document g = new Document("orderId", orderId).append("orderCode", orderId).append("orderedAt", Date.from(Instant.now()))
                    .append("customer", new Document("id", P + "k1").append("name", "Bình").append("phone", "0912"))
                    .append("items", List.of(
                            new Document("productId", P + "milk").append("sku", "M").append("name", "Milk").append("quantity", 2).append("gift", false),
                            new Document("productId", P + "bread").append("sku", "B").append("name", "Bread").append("quantity", 1).append("gift", false)))
                    .append("allocations", List.of(new Document("batchId", P + "b1").append("batchCode", P + "L1").append("quantity", 2)));
            GraphRows.addOrder(g, rows);
            writer.writeOrders(driver, rows);
            writer.writeOrders(driver, rows); // ghi lặp
        }
        List<GraphQueryService.TracedOrder> trace = query.trace(P + "L1");
        assertEquals(2, trace.size(), "ghi lặp không tạo quan hệ trùng");
        assertTrue(trace.stream().allMatch(t -> t.quantity() == 2 && "0912".equals(t.customerPhone())));
    }
}
