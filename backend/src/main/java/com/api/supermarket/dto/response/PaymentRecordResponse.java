package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRecordResponse(Long paymentRecordId, Long orderId, Long returnId, String eventType, String reference,
        BigDecimal amount, String paymentMethod, Long confirmedBy, LocalDateTime createdAt) {
}
