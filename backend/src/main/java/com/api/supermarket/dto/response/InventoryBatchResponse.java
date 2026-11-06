package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.*;
import com.api.supermarket.entity.InventoryBatch;

public record InventoryBatchResponse(Long batchId, Long productId, String supplierName, String batchCode,
        int receivedQuantity, int remainingQuantity, int damagedQuantity,
        int availableQuantity, BigDecimal unitCost, LocalDate manufacturingDate, LocalDate expiryDate,
        LocalDateTime receivedAt, String receivedBy, String status) {
    public static InventoryBatchResponse from(InventoryBatch batch) {
        int available = "ACTIVE".equals(batch.status)
                && (batch.expiryDate == null || !batch.expiryDate.isBefore(LocalDate.now()))
                ? batch.remaining : 0;
        return new InventoryBatchResponse(batch.id, batch.productId, batch.supplierName, batch.code,
                batch.received, batch.remaining, batch.damaged, available, batch.unitCost,
                batch.manufacturingDate, batch.expiryDate, batch.receivedAt, batch.receivedBy, batch.status);
    }
}
