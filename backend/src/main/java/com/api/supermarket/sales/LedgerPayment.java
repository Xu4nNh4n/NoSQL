package com.api.supermarket.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.bson.Document;

/**
 * Một dòng sổ thanh toán Cassandra (bảng {@code payments_by_order} và {@code payments_by_cashier_day}).
 * Tiền lưu dạng chuỗi trong payload sync_queue để không phụ thuộc cách chuyển BigDecimal của Document.
 */
public record LedgerPayment(UUID paymentId, String orderId, String orderCode, String type, String method,
        BigDecimal amount, String reference, String returnCode, String cashierId, Instant createdAt) {

    public Document toDocument() {
        return new Document("paymentId", paymentId.toString()).append("orderId", orderId).append("orderCode", orderCode)
                .append("type", type).append("method", method).append("amount", amount.toPlainString())
                .append("reference", reference).append("returnCode", returnCode).append("cashierId", cashierId)
                .append("createdAt", Date.from(createdAt));
    }

    public static LedgerPayment fromDocument(Document d) {
        return new LedgerPayment(UUID.fromString(d.getString("paymentId")), d.getString("orderId"),
                d.getString("orderCode"), d.getString("type"), d.getString("method"), new BigDecimal(d.getString("amount")),
                d.getString("reference"), d.getString("returnCode"), d.getString("cashierId"),
                d.getDate("createdAt").toInstant());
    }
}
