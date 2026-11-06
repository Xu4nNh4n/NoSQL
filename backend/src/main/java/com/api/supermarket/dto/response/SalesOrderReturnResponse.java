package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SalesOrderReturnResponse(Long returnId, Long orderId, String requestKey,
        BigDecimal refundAmount, String refundStatus, LocalDateTime createdAt, List<Item> items) {
    public record Item(Long orderItemId, Long productId, Long batchId, String productName, Integer quantity,
            boolean restock, String reason) { }
}
