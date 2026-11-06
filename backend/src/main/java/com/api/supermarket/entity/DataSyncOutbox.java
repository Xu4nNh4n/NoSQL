package com.api.supermarket.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_sync_outbox")
public class DataSyncOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "event_key", nullable = false, unique = true, length = 150)
    public String eventKey;
    @Column(nullable = false, length = 30) public String destination;
    @Column(nullable = false, columnDefinition = "LONGTEXT") public String payload;
    @Column(nullable = false, length = 20) public String status = "PENDING";
    @Column(nullable = false) public int attempts;
    @Column(name = "next_attempt_at", nullable = false) public LocalDateTime nextAttemptAt;
    @Column(name = "created_at", nullable = false) public LocalDateTime createdAt;
    @Column(name = "delivered_at") public LocalDateTime deliveredAt;
    @Column(name = "last_error", length = 255) public String lastError;
}
