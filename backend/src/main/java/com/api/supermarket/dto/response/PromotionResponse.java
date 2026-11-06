package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionResponse(Long promotionId, Long productId, String productName, String name,
        BigDecimal percent, LocalDateTime startsAt, LocalDateTime endsAt, boolean active) {}
