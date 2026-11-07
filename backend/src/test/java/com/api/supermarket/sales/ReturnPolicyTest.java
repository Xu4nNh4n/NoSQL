package com.api.supermarket.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ReturnPolicyTest {
    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void srsExampleBuy2Get1EachUnitRefundsAShare() {
        // Mua 2 tặng 1 sữa 30k: 3 hộp, net 60k → mỗi hộp hoàn 20k
        assertEquals(vnd(20_000), ReturnPolicy.refund(vnd(60_000), 3, 0, 1));
        assertEquals(vnd(40_000), ReturnPolicy.refund(vnd(60_000), 3, 1, 2));
    }

    @Test
    void returningEverythingInPiecesRefundsExactlyNet() {
        BigDecimal net = vnd(100_000);
        BigDecimal total = ReturnPolicy.refund(net, 3, 0, 1).add(ReturnPolicy.refund(net, 3, 1, 1))
                .add(ReturnPolicy.refund(net, 3, 2, 1));
        assertEquals(net, total);
        assertEquals(vnd(33_333), ReturnPolicy.refund(net, 3, 0, 1));
        assertEquals(vnd(33_334), ReturnPolicy.refund(net, 3, 2, 1), "đơn vị cuối nhận phần dư");
    }

    @Test
    void orderOfReturnsDoesNotChangeTotal() {
        BigDecimal net = vnd(99_999);
        BigDecimal a = ReturnPolicy.refund(net, 7, 0, 3).add(ReturnPolicy.refund(net, 7, 3, 4));
        BigDecimal b = ReturnPolicy.refund(net, 7, 0, 5).add(ReturnPolicy.refund(net, 7, 5, 2));
        assertEquals(net, a);
        assertEquals(net, b);
    }

    @Test
    void rejectsReturningMoreThanBought() {
        assertThrows(IllegalArgumentException.class, () -> ReturnPolicy.refund(vnd(10), 2, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> ReturnPolicy.refund(vnd(10), 2, 0, 0));
    }
}
