package com.api.supermarket.inventory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.bson.Document;

/**
 * Một dòng thẻ kho (bảng Cassandra {@code stock_movements_by_product}). Dấu số lượng theo SRS FR-05:
 * RECEIPT, RETURN dương; SALE, ADJUSTMENT âm; RETURN_DAMAGED dương nhưng không cộng tồn bán.
 * {@code id} tạo từ nội dung nên ghi lại cùng sự kiện chỉ ghi đè đúng dòng cũ.
 */
public record StockMovement(UUID id, String productId, Instant createdAt, String type, int quantity,
        String batchCode, String refType, String refCode, String note, String performedBy) {

    public static final String RECEIPT = "RECEIPT";
    public static final String SALE = "SALE";
    public static final String RETURN = "RETURN";
    public static final String RETURN_DAMAGED = "RETURN_DAMAGED";
    public static final String ADJUSTMENT = "ADJUSTMENT";

    public static UUID idFor(String... parts) {
        return UUID.nameUUIDFromBytes(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
    }

    public Document toDocument() {
        return new Document("id", id.toString()).append("productId", productId).append("createdAt", Date.from(createdAt))
                .append("type", type).append("quantity", quantity).append("batchCode", batchCode)
                .append("refType", refType).append("refCode", refCode).append("note", note)
                .append("performedBy", performedBy);
    }

    public static StockMovement fromDocument(Document d) {
        return new StockMovement(UUID.fromString(d.getString("id")), d.getString("productId"),
                d.getDate("createdAt").toInstant(), d.getString("type"), d.getInteger("quantity"),
                d.getString("batchCode"), d.getString("refType"), d.getString("refCode"), d.getString("note"),
                d.getString("performedBy"));
    }
}
