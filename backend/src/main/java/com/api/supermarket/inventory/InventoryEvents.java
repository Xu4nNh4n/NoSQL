package com.api.supermarket.inventory;

import java.util.List;

import org.bson.Document;

/**
 * Loại sự kiện và cấu trúc payload của {@code sync_queue}. Payload mang sẵn dữ liệu cần ghi:
 * {@code movements} cho thẻ kho Cassandra, {@code batches} cho đồ thị Neo4j (Batch → Product, Supplier).
 */
public final class InventoryEvents {
    public static final String BATCH_RECEIVED = "BATCH_RECEIVED";
    public static final String STOCK_ADJUSTED = "STOCK_ADJUSTED";
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String RETURN_CREATED = "RETURN_CREATED";

    private InventoryEvents() {
    }

    public static Document payload(List<StockMovement> movements, List<Batch> batches) {
        return new Document("movements", movements.stream().map(StockMovement::toDocument).toList())
                .append("batches", batches.stream().map(InventoryEvents::batchNode).toList());
    }

    static Document batchNode(Batch b) {
        return new Document("batchId", b.getId()).append("batchCode", b.getBatchCode())
                .append("productId", b.getProductId()).append("sku", b.getProductSku())
                .append("productName", b.getProductName())
                .append("expiryDate", b.getExpiryDate() == null ? null : b.getExpiryDate().toString())
                .append("supplierId", b.getSupplierId()).append("supplierName", b.getSupplierName());
    }

    public static List<StockMovement> movements(Document payload) {
        List<Document> raw = payload == null ? null : payload.getList("movements", Document.class);
        return raw == null ? List.of() : raw.stream().map(StockMovement::fromDocument).toList();
    }
}
