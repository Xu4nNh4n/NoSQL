package com.api.supermarket.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ReturnPolicyTest {

    @Test
    void refundAmount_AllocatesDiscountToActualReturnedQuantity() {
        assertEquals(new BigDecimal("7.50"), ReturnPolicy.refundAmount(2, 0, 1,
                new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("20.00"), new BigDecimal("15.00")));
    }

    @Test
    void refundAmount_RepeatedUnitsIncludeFinalRoundingCent() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int returned = 0; returned < 3; returned++) {
            sum = sum.add(ReturnPolicy.refundAmount(3, returned, 1, BigDecimal.ONE,
                    BigDecimal.ZERO, new BigDecimal("3.00"), BigDecimal.ONE));
        }
        assertEquals(new BigDecimal("1.00"), sum);
    }

    @Test
    void refundAmount_SeparateLinesAllocateAllPaidCents() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int offset = 2; offset >= 0; offset--) {
            sum = sum.add(ReturnPolicy.refundAmount(1, 0, 1, BigDecimal.ONE,
                    BigDecimal.valueOf(offset), new BigDecimal("3.00"), BigDecimal.ONE));
        }
        assertEquals(new BigDecimal("1.00"), sum);
    }

    @Test
    void refundAmount_RejectsInvalidInvoiceAmounts() {
        assertThrows(IllegalArgumentException.class, () -> ReturnPolicy.refundAmount(1, 0, 1,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN));
        assertEquals(new BigDecimal("0.00"), ReturnPolicy.refundAmount(1, 0, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("ReturnPolicy: Tính chính xác số tiền hoàn khi thông tin hợp lệ")
    void refundAmount_ValidInputs_ShouldCalculateCorrectRefund() {
        BigDecimal refund = ReturnPolicy.refundAmount(5, 0, 2, new BigDecimal("45000.00"));

        assertNotNull(refund);
        assertEquals(new BigDecimal("90000.00"), refund);
    }

    @Test
    @DisplayName("ReturnPolicy: Tính chính xác số tiền hoàn khi trả hàng nhiều đợt (partial returns)")
    void refundAmount_MultiplePartialReturns_ShouldSucceed() {
        // Đã mua 10 món, đợt trước trả 3 món, đợt này trả tiếp 4 món
        BigDecimal refund = ReturnPolicy.refundAmount(10, 3, 4, new BigDecimal("20000.00"));

        assertEquals(new BigDecimal("80000.00"), refund);
    }

    @Test
    @DisplayName("ReturnPolicy: Ném ngoại lệ khi số lượng yêu cầu trả vượt quá số lượng còn lại")
    void refundAmount_RequestedExceedsRemaining_ShouldThrowException() {
        // Đã mua 5, đã trả 3 -> còn 2; yêu cầu trả 3 món -> vượt quá
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(5, 3, 3, new BigDecimal("10000.00")));
        assertEquals("Số lượng trả vượt số lượng còn lại của mặt hàng", ex.getMessage());
    }

    @Test
    @DisplayName("ReturnPolicy: Ném ngoại lệ khi số lượng mua không hợp lệ")
    void refundAmount_InvalidPurchasedQuantity_ShouldThrowException() {
        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(0, 0, 1, new BigDecimal("10000.00")));
        assertEquals("Số lượng trả vượt số lượng còn lại của mặt hàng", exZero.getMessage());

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(-5, 0, 1, new BigDecimal("10000.00")));
        assertEquals("Số lượng trả vượt số lượng còn lại của mặt hàng", exNeg.getMessage());
    }

    @Test
    @DisplayName("ReturnPolicy: Ném ngoại lệ khi số lượng yêu cầu trả <= 0")
    void refundAmount_ZeroOrNegativeRequestedQuantity_ShouldThrowException() {
        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(5, 0, 0, new BigDecimal("10000.00")));
        assertEquals("Số lượng trả vượt số lượng còn lại của mặt hàng", exZero.getMessage());

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(5, 0, -2, new BigDecimal("10000.00")));
        assertEquals("Số lượng trả vượt số lượng còn lại của mặt hàng", exNeg.getMessage());
    }

    @Test
    @DisplayName("ReturnPolicy: Ném ngoại lệ khi đơn giá null hoặc âm")
    void refundAmount_InvalidUnitPrice_ShouldThrowException() {
        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(5, 0, 2, null));
        assertEquals("Đơn giá gốc không hợp lệ", exNull.getMessage());

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class,
                () -> ReturnPolicy.refundAmount(5, 0, 2, new BigDecimal("-10000.00")));
        assertEquals("Đơn giá gốc không hợp lệ", exNeg.getMessage());
    }
}
