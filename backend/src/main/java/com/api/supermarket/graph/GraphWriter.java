package com.api.supermarket.graph;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.sync.SyncHandler;
import com.api.supermarket.sync.SyncTask;

/**
 * Ghi đồ thị Neo4j từ sự kiện lô hàng và đơn bán. Mọi câu đều {@code UNWIND $rows ... MERGE} theo {@code id},
 * nên chạy lại bao nhiêu lần cũng không tạo node/quan hệ trùng.
 */
@Component
public class GraphWriter implements SyncHandler {
    private static final Logger log = LoggerFactory.getLogger(GraphWriter.class);

    static final String MERGE_BATCHES = """
            UNWIND $rows AS r
            MERGE (b:Batch {id: r.batchId}) SET b.code = r.batchCode, b.expiry = r.expiryDate
            MERGE (p:Product {id: r.productId}) SET p.sku = r.sku, p.name = r.productName
            MERGE (b)-[:OF_PRODUCT]->(p)
            WITH b, r WHERE r.supplierId IS NOT NULL
            MERGE (s:Supplier {id: r.supplierId}) SET s.name = r.supplierName
            MERGE (b)-[:SUPPLIED_BY]->(s)""";
    static final String MERGE_ORDERS = """
            UNWIND $rows AS r
            MERGE (o:Order {id: r.orderId}) SET o.code = r.orderCode, o.at = r.orderedAt""";
    static final String MERGE_CUSTOMERS = """
            UNWIND $rows AS r
            MATCH (o:Order {id: r.orderId})
            MERGE (c:Customer {id: r.id}) SET c.name = r.name, c.phone = r.phone
            MERGE (c)-[:PLACED]->(o)""";
    static final String MERGE_ITEMS = """
            UNWIND $rows AS r
            MATCH (o:Order {id: r.orderId})
            MERGE (p:Product {id: r.productId}) SET p.sku = r.sku, p.name = r.name
            MERGE (o)-[c:CONTAINS]->(p) SET c.qty = r.quantity, c.gift = r.gift""";
    static final String MERGE_ALLOCATIONS = """
            UNWIND $rows AS r
            MATCH (o:Order {id: r.orderId})
            MERGE (b:Batch {id: r.batchId}) ON CREATE SET b.code = r.batchCode
            MERGE (o)-[t:TOOK_FROM]->(b) SET t.qty = r.quantity""";

    private final ObjectProvider<Driver> driver;

    public GraphWriter(ObjectProvider<Driver> driver) {
        this.driver = driver;
    }

    @Override
    public Set<String> types() {
        return Set.of(InventoryEvents.BATCH_RECEIVED, InventoryEvents.ORDER_CREATED);
    }

    @Override
    public void apply(SyncTask task) {
        Driver neo4j = driver.getIfAvailable();
        if (neo4j == null) {
            log.debug("Chưa cấu hình Neo4j, bỏ qua ghi đồ thị cho {}", task.getId());
            return;
        }
        if (InventoryEvents.BATCH_RECEIVED.equals(task.getType())) {
            writeBatches(neo4j, GraphRows.batches(task.getPayload()));
        } else {
            GraphRows.OrderRows rows = GraphRows.OrderRows.empty();
            GraphRows.addOrder(task.getPayload() == null ? null : task.getPayload().get("graph", org.bson.Document.class), rows);
            writeOrders(neo4j, rows);
        }
    }

    void writeBatches(Driver neo4j, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try (Session session = neo4j.session()) {
            session.executeWriteWithoutResult(tx -> tx.run(MERGE_BATCHES, Map.of("rows", rows)).consume());
        }
    }

    void writeOrders(Driver neo4j, GraphRows.OrderRows rows) {
        if (rows.isEmpty()) {
            return;
        }
        try (Session session = neo4j.session()) {
            session.executeWriteWithoutResult(tx -> {
                tx.run(MERGE_ORDERS, Map.of("rows", rows.orders())).consume();
                if (!rows.customers().isEmpty()) {
                    tx.run(MERGE_CUSTOMERS, Map.of("rows", rows.customers())).consume();
                }
                if (!rows.items().isEmpty()) {
                    tx.run(MERGE_ITEMS, Map.of("rows", rows.items())).consume();
                }
                if (!rows.allocations().isEmpty()) {
                    tx.run(MERGE_ALLOCATIONS, Map.of("rows", rows.allocations())).consume();
                }
            });
        }
    }
}
