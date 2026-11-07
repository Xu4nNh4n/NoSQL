package com.api.supermarket.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiền hoàn của một lần trả (spec 10.4): trả {@code k} đơn vị của một dòng mua {@code Q} đơn vị, đã trả trước đó
 * {@code r} đơn vị, dòng có tiền thực trả {@code net}:
 * <pre>floor(net × (r + k) / Q) − floor(net × r / Q)</pre>
 * Ranh giới cộng dồn cố định nên trả theo thứ tự nào cũng vậy, và trả hết luôn hoàn đúng {@code net}.
 */
public final class ReturnPolicy {
    private ReturnPolicy() {
    }

    public static BigDecimal refund(BigDecimal net, int quantity, int alreadyReturned, int requested) {
        if (quantity <= 0 || alreadyReturned < 0 || requested <= 0 || alreadyReturned + requested > quantity) {
            throw new IllegalArgumentException("Số lượng trả vượt số lượng còn lại của dòng");
        }
        if (net == null || net.signum() < 0) {
            throw new IllegalArgumentException("Tiền dòng không hợp lệ");
        }
        BigDecimal q = BigDecimal.valueOf(quantity);
        BigDecimal after = net.multiply(BigDecimal.valueOf(alreadyReturned + requested)).divide(q, 0, RoundingMode.FLOOR);
        BigDecimal before = net.multiply(BigDecimal.valueOf(alreadyReturned)).divide(q, 0, RoundingMode.FLOOR);
        return after.subtract(before);
    }
}
