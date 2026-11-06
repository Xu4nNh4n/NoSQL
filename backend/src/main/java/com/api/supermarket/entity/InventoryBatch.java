package com.api.supermarket.entity;

import java.math.BigDecimal;
import java.time.*;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory_batches")
public class InventoryBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id") public Long id;
    @Column(name = "product_id", nullable = false) public Long productId;
    @Column(name = "supplier_name", nullable = false, length = 150) public String supplierName;
    @Column(name = "supplier_id") public Long supplierId;
    @Column(name = "batch_code", nullable = false, unique = true, length = 100) public String code;
    @Column(name = "received_quantity", nullable = false) public int received;
    @Column(name = "remaining_quantity", nullable = false) public int remaining;
    @Column(name = "damaged_quantity", nullable = false) public int damaged;
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2) public BigDecimal unitCost;
    @Column(name = "manufacturing_date") public LocalDate manufacturingDate;
    @Column(name = "expiry_date") public LocalDate expiryDate;
    @Column(name = "received_at", nullable = false) public LocalDateTime receivedAt;
    @Column(name = "received_by", nullable = false, length = 100) public String receivedBy;
    @Column(name = "status", nullable = false, length = 30) public String status = "ACTIVE";
}
