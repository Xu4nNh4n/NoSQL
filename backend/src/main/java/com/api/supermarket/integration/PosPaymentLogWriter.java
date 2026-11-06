package com.api.supermarket.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PosPaymentLogWriter {
    private final ObjectMapper json;
    private final Path directory;

    public PosPaymentLogWriter(ObjectMapper json,
            @Value("${integration.pos-payment-log-directory:../analytics/pos-payments}") String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("POS payment log directory must not be blank");
        }
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public void write(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Payment payload must be an object");
        }
        String eventId = text(payload, "eventId");
        if (!UUID.fromString(eventId).toString().equals(eventId)) {
            throw new IllegalArgumentException("eventId must be a canonical UUID");
        }
        String eventType = text(payload, "eventType");
        if (!"PAYMENT_RECEIVED".equals(eventType) && !"REFUND_CONFIRMED".equals(eventType)) {
            throw new IllegalArgumentException("Unsupported POS payment event type: " + eventType);
        }
        JsonNode timestamp = payload.get("eventTime");
        if (timestamp == null || !timestamp.isIntegralNumber() || !timestamp.canConvertToLong()) {
            throw new IllegalArgumentException("eventTime must be epoch milliseconds as a long");
        }
        Instant time = Instant.ofEpochMilli(timestamp.longValue());
        String date = text(payload, "eventDate");
        LocalDate day = LocalDate.parse(date);
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !day.equals(time.atOffset(ZoneOffset.UTC).toLocalDate())) {
            throw new IllegalArgumentException("eventDate must match the UTC eventTime day");
        }
        long orderId = positiveId(payload, "orderId");
        JsonNode amount = payload.get("amount");
        if (amount == null || !amount.isNumber() || !Double.isFinite(amount.doubleValue())) {
            throw new IllegalArgumentException("amount must be a finite JSON number");
        }
        String method = text(payload, "paymentMethod");
        String status = text(payload, "status");
        try {
            JsonNode metadata = json.readTree(text(payload, "metadata"));
            if (metadata == null || !metadata.isObject()) {
                throw new IllegalArgumentException("metadata must contain a JSON object");
            }
            ObjectNode record = json.createObjectNode();
            record.put("event_id", eventId);
            record.put("event_time", DateTimeFormatter.ISO_INSTANT.format(time));
            record.put("payment_record_id", positiveId(metadata, "paymentRecordId"));
            record.put("order_id", orderId);
            record.put("event_type", eventType);
            record.set("amount", amount);
            record.put("payment_method", method);
            record.put("status", status);
            // Fingerprint the entire input, including metadata and fields outside the projection.
            ObjectNode canonicalPayload = payload.deepCopy();
            canonicalPayload.set("metadata", metadata);
            record.put("payload_sha256", fingerprint(canonicalPayload));
            persist(day, eventId, record);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot write POS payment log for " + eventId, ex);
        }
    }

    private void persist(LocalDate day, String eventId, ObjectNode record) throws IOException {
        // Serialize in this JVM and across cooperating processes before checking or publishing.
        synchronized (PosPaymentLogWriter.class) {
            Files.createDirectories(directory);
            try (FileChannel channel = FileChannel.open(directory.resolve(".writer.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                Path partition = directory.resolve(day.toString());
                Files.createDirectories(partition);
                Path target = partition.resolve(eventId + ".json");
                // Reusing an event UUID on another day is also a conflicting replay.
                try (var partitions = Files.list(directory)) {
                    for (Path other : partitions.filter(Files::isDirectory).toList()) {
                        if (!other.equals(partition) && Files.exists(other.resolve(eventId + ".json"))) {
                            throw new IllegalStateException("Conflicting POS payment partition for " + eventId);
                        }
                    }
                }
                if (Files.exists(target)) {
                    JsonNode existing = json.readTree(Files.readAllBytes(target));
                    if (!json.readTree(json.writeValueAsBytes(record)).equals(existing)) {
                        throw new IllegalStateException("Conflicting POS payment payload for " + eventId);
                    }
                    return;
                }
                Path temporary = Files.createTempFile(partition, ".payment-", ".tmp");
                Throwable failure = null;
                try {
                    Files.write(temporary, json.writeValueAsBytes(record));
                    // No fallback: an unsupported atomic move must fail the outbox delivery.
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException | RuntimeException | Error ex) {
                    failure = ex;
                    throw ex;
                } finally {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ex) {
                        if (failure != null) failure.addSuppressed(ex);
                        else throw ex;
                    }
                }
            }
        }
    }

    private String fingerprint(JsonNode payload) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(canonical(payload))));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = json.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) sorted.set(name, canonical(node.get(name)));
            return sorted;
        }
        if (node.isArray()) {
            var array = json.createArrayNode();
            node.forEach(value -> array.add(canonical(value)));
            return array;
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a nonblank string");
        }
        return value.textValue();
    }

    private static long positiveId(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive long integer");
        }
        return value.longValue();
    }
}
