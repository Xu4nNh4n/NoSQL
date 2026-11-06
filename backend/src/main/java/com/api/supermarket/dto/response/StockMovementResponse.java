package com.api.supermarket.dto.response;

import java.time.LocalDateTime;

public record StockMovementResponse(Long movementId, Long productId, Long orderId,
        String movementType, Integer quantity, String note, String performedBy, LocalDateTime createdAt) {
}
