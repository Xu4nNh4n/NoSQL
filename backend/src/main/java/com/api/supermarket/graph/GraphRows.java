package com.api.supermarket.graph;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

/**
 * Chuyển payload sự kiện (Document) thành các danh sách tham số cho câu Cypher {@code UNWIND $rows}.
 * Cộng gộp số lượng theo (đơn, sản phẩm) và (đơn, lô) vì MERGE một quan hệ hai lần sẽ ghi đè thuộc tính.
 */
final class GraphRows {
    private GraphRows() {
    }

    record OrderRows(List<Map<String, Object>> orders,
            List<Map<String, Object>> items, List<Map<String, Object>> allocations) {

        static OrderRows empty() {
            return new OrderRows(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        boolean isEmpty() {
            return orders.isEmpty();
        }
    }

    static List<Map<String, Object>> batches(Document payload) {
        List<Document> raw = payload == null ? null : payload.getList("batches", Document.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (raw == null) {
            return rows;
        }
        for (Document b : raw) {
            Map<String, Object> r = new HashMap<>();
            r.put("batchId", b.getString("batchId"));
            r.put("batchCode", b.getString("batchCode"));
            r.put("expiryDate", b.getString("expiryDate"));
            r.put("productId", b.getString("productId"));
            r.put("sku", b.getString("sku"));
            r.put("productName", b.getString("productName"));
            r.put("supplierId", b.getString("supplierId"));
            r.put("supplierName", b.getString("supplierName"));
            rows.add(r);
        }
        return rows;
    }

    /** Thêm một đơn (phần {@code graph} của payload ORDER_CREATED) vào {@code into}. */
    static void addOrder(Document graph, OrderRows into) {
        if (graph == null) {
            return;
        }
        String orderId = graph.getString("orderId");
        Map<String, Object> o = new HashMap<>();
        o.put("orderId", orderId);
        o.put("orderCode", graph.getString("orderCode"));
        Date at = graph.getDate("orderedAt");
        o.put("orderedAt", at == null ? null : OffsetDateTime.ofInstant(at.toInstant(), ZoneOffset.UTC));
        into.orders().add(o);

        Map<String, Map<String, Object>> items = new LinkedHashMap<>();
        for (Document i : graph.getList("items", Document.class, List.of())) {
            String pid = i.getString("productId");
            Map<String, Object> row = items.computeIfAbsent(pid, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("orderId", orderId);
                m.put("productId", pid);
                m.put("sku", i.getString("sku"));
                m.put("name", i.getString("name"));
                m.put("quantity", 0L);
                m.put("gift", true);
                return m;
            });
            row.put("quantity", (Long) row.get("quantity") + i.getInteger("quantity"));
            row.put("gift", (Boolean) row.get("gift") && Boolean.TRUE.equals(i.getBoolean("gift")));
        }
        into.items().addAll(items.values());

        Map<String, Map<String, Object>> allocs = new LinkedHashMap<>();
        for (Document a : graph.getList("allocations", Document.class, List.of())) {
            String bid = a.getString("batchId");
            Map<String, Object> row = allocs.computeIfAbsent(bid, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("orderId", orderId);
                m.put("batchId", bid);
                m.put("batchCode", a.getString("batchCode"));
                m.put("quantity", 0L);
                return m;
            });
            row.put("quantity", (Long) row.get("quantity") + a.getInteger("quantity"));
        }
        into.allocations().addAll(allocs.values());
    }
}
