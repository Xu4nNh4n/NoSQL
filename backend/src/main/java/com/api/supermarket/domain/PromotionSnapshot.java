package com.api.supermarket.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable sale-time values; changing a promotion never changes an invoice. */
public record PromotionSnapshot(Long promotionId, String name, BigDecimal percent,
        LocalDateTime startsAt, LocalDateTime endsAt) {}
