package com.api.supermarket.integration;

import com.api.supermarket.entity.*;
import com.api.supermarket.repository.DataSyncOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DataSyncPublisher {
    private final DataSyncOutboxRepository outbox;
    private final ObjectMapper json;

    public DataSyncPublisher(DataSyncOutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    public void movement(StockMovement movement) {
        String type = switch (movement.getMovementType()) {
            case "RECEIPT", "OPENING" -> "INVENTORY_RECEIVED";
            case "SALE" -> "INVENTORY_SOLD";
            case "RESERVE" -> "INVENTORY_RESERVED";
            case "RELEASE" -> "INVENTORY_RELEASED";
            case "RETURN" -> "INVENTORY_RETURNED";
            case "RETURN_DAMAGED" -> "INVENTORY_QUARANTINED";
            default -> "INVENTORY_ADJUSTED";
        };
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("movementId", movement.getMovementId());
        metadata.put("movementType", movement.getMovementType());
        metadata.put("note", movement.getNote());
        metadata.put("performedBy", movement.getPerformedBy());
        event("MOVEMENT-" + movement.getMovementId(), type, movement.getOrderId(),
                movement.getProduct().getSkuCode(), movement.getQuantity(), BigDecimal.ZERO,
                null, "COMPLETED", metadata, movement.getCreatedAt());
    }

    public void payment(PaymentRecord payment) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("paymentRecordId", payment.getPaymentRecordId());
        metadata.put("returnId", payment.getReturnId());
        metadata.put("reference", payment.getReference());
        String type = "REFUND".equals(payment.getEventType()) ? "REFUND_CONFIRMED" : "PAYMENT_RECEIVED";
        event("PAYMENT-" + payment.getPaymentRecordId(), type, payment.getOrderId(), null,
                null, payment.getAmount(), payment.getPaymentMethod(), "COMPLETED", metadata, payment.getCreatedAt());
    }

    public void order(SalesOrder order, String type, String suffix, BigDecimal amount, Map<String, Object> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>(extra);
        metadata.put("orderCode", order.getOrderCode());
        metadata.put("orderType", order.getOrderType());
        metadata.put("paymentStatus", order.getPaymentStatus());
        event(type + "-" + order.getOrderId() + "-" + suffix, type, order.getOrderId(), null,
                null, amount, order.getPaymentMethod(), order.getStatus(), metadata, null);
    }

    private void event(String key, String type, Long orderId, String sku, Integer quantity,
            BigDecimal amount, String paymentMethod, String status, Map<String, Object> metadata,
            LocalDateTime sourceTime) {
        Instant time = sourceTime == null ? Instant.now() : sourceTime.atZone(ZoneId.systemDefault()).toInstant();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
        payload.put("eventTime", time.toEpochMilli());
        payload.put("eventDate", time.atZone(ZoneOffset.UTC).toLocalDate().toString());
        payload.put("eventType", type);
        payload.put("orderId", orderId);
        payload.put("productSku", sku);
        payload.put("quantity", quantity);
        payload.put("amount", amount);
        payload.put("paymentMethod", paymentMethod);
        payload.put("status", status);
        payload.put("metadata", serialize(metadata));
        enqueue(key, "ASTRA", payload);
        if ("PAYMENT_RECEIVED".equals(type) || "REFUND_CONFIRMED".equals(type)) {
            enqueue("POSLOG-" + key, "POS_LOG", payload);
        }
    }

    private void enqueue(String key, String destination, Map<String, Object> payload) {
        if (outbox.existsByEventKey(key)) return;
        DataSyncOutbox entry = new DataSyncOutbox();
        entry.eventKey = key;
        entry.destination = destination;
        entry.payload = serialize(payload);
        entry.createdAt = LocalDateTime.now();
        entry.nextAttemptAt = entry.createdAt;
        outbox.save(entry);
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể tạo dữ liệu đồng bộ", ex);
        }
    }
}
