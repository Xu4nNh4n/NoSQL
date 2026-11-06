package com.api.supermarket.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rules shared by relational and document-storage implementations. */
public final class ReturnPolicy {
    private ReturnPolicy() {}
    public static BigDecimal refundAmount(int purchased, int alreadyReturned, int requested, BigDecimal unitPrice) {
        if (purchased <= 0 || alreadyReturned < 0 || alreadyReturned > purchased
                || requested <= 0 || requested > purchased - alreadyReturned) {
            throw new IllegalArgumentException("Số lượng trả vượt số lượng còn lại của mặt hàng");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Đơn giá gốc không hợp lệ");
        }
        return unitPrice.multiply(BigDecimal.valueOf(requested));
    }

    public static BigDecimal refundAmount(int purchased, int alreadyReturned, int requested, BigDecimal unitPrice,
            BigDecimal lineOffset, BigDecimal orderSubtotal, BigDecimal paidTotal) {
        BigDecimal gross = refundAmount(purchased, alreadyReturned, requested, unitPrice);
        if (lineOffset == null || orderSubtotal == null || paidTotal == null
                || lineOffset.signum() < 0 || orderSubtotal.signum() < 0 || paidTotal.signum() < 0
                || paidTotal.compareTo(orderSubtotal) > 0
                || lineOffset.add(unitPrice.multiply(BigDecimal.valueOf(purchased))).compareTo(orderSubtotal) > 0) {
            throw new IllegalArgumentException("Số tiền hóa đơn gốc không hợp lệ để phân bổ hoàn tiền");
        }
        if (orderSubtotal.signum() == 0) return new BigDecimal("0.00");
        BigDecimal before = lineOffset.add(unitPrice.multiply(BigDecimal.valueOf(alreadyReturned)));
        // Fixed cumulative boundaries allocate every cent once, regardless of return order.
        return before.add(gross).multiply(paidTotal).divide(orderSubtotal, 2, RoundingMode.DOWN)
                .subtract(before.multiply(paidTotal).divide(orderSubtotal, 2, RoundingMode.DOWN));
    }
}
