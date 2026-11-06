package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.api.supermarket.domain.PromotionSnapshot;

public record PosQuoteResponse(String quoteToken, LocalDateTime quotedAt, String timezone,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount, List<Item> items) {
    public record Item(Long productId, int quantity, BigDecimal originalUnitPrice,
            BigDecimal netUnitPrice, BigDecimal grossSubtotal, BigDecimal discountAmount,
            BigDecimal subTotal, PromotionSnapshot promotionSnapshot) {}
}
