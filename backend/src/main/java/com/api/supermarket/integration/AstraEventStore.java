package com.api.supermarket.integration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AstraEventStore {
    private final ObjectProvider<CqlSession> sessions;
    private volatile PreparedStatement insert;

    public AstraEventStore(ObjectProvider<CqlSession> sessions) {
        this.sessions = sessions;
    }

    public void write(JsonNode event) {
        CqlSession session = sessions.getObject();
        var builder = statement(session).boundStatementBuilder()
                .setLocalDate("event_date", LocalDate.parse(event.path("eventDate").asText()))
                .setInstant("event_time", Instant.ofEpochMilli(event.path("eventTime").asLong()))
                .setUuid("event_id", UUID.fromString(event.path("eventId").asText()))
                .setString("event_type", event.path("eventType").asText())
                .setBigDecimal("amount", new BigDecimal(event.path("amount").asText()))
                .setString("status", event.path("status").asText())
                .setString("metadata", event.path("metadata").asText());
        if (event.path("orderId").isNull()) builder.setToNull("order_id");
        else builder.setLong("order_id", event.path("orderId").asLong());
        if (event.path("quantity").isNull()) builder.setToNull("quantity");
        else builder.setInt("quantity", event.path("quantity").asInt());
        builder.setString("product_sku", event.path("productSku").isNull() ? null : event.path("productSku").asText());
        builder.setString("payment_method", event.path("paymentMethod").isNull() ? null : event.path("paymentMethod").asText());
        session.execute(builder.build());
    }

    private synchronized PreparedStatement statement(CqlSession session) {
        if (insert == null) {
            insert = session.prepare("INSERT INTO business_events_by_day "
                    + "(event_date, event_time, event_id, event_type, order_id, product_sku, quantity, amount, "
                    + "payment_method, status, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        }
        return insert;
    }
}
