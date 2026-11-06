package com.api.supermarket.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FefoPolicyTest {

    private final LocalDate today = LocalDate.of(2026, 10, 7);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 7, 10, 0);

    @Test
    @DisplayName("FEFO: Ưu tiên cấp phát lô có hạn sử dụng gần nhất trước")
    void allocate_ShouldPickLotWithEarliestExpiryFirst() {
        FefoPolicy.Lot lot1 = new FefoPolicy.Lot(1L, 10, today.plusDays(30), now);
        FefoPolicy.Lot lot2 = new FefoPolicy.Lot(2L, 10, today.plusDays(10), now.plusHours(1));

        List<FefoPolicy.Pick> plan = FefoPolicy.allocate(List.of(lot1, lot2), 5, today);

        assertNotNull(plan);
        assertEquals(1, plan.size());
        assertEquals(2L, plan.get(0).lotId()); // lot2 has earlier expiry
        assertEquals(5, plan.get(0).quantity());
    }

    @Test
    @DisplayName("FEFO: Khi hạn sử dụng giống nhau, ưu tiên lô nhập trước (FIFO)")
    void allocate_WhenSameExpiryDate_ShouldPickEarlierReceivedAtFirst() {
        FefoPolicy.Lot lot1 = new FefoPolicy.Lot(1L, 10, today.plusDays(20), now.plusHours(2));
        FefoPolicy.Lot lot2 = new FefoPolicy.Lot(2L, 10, today.plusDays(20), now.plusHours(1)); // received earlier

        List<FefoPolicy.Pick> plan = FefoPolicy.allocate(List.of(lot1, lot2), 5, today);

        assertEquals(1, plan.size());
        assertEquals(2L, plan.get(0).lotId());
        assertEquals(5, plan.get(0).quantity());
    }

    @Test
    @DisplayName("FEFO: Khi số lượng vượt lô đầu tiên, chia nhỏ và lấy tiếp từ lô kế tiếp")
    void allocate_WhenQuantityExceedsFirstLot_ShouldSplitAcrossMultipleLots() {
        FefoPolicy.Lot lot1 = new FefoPolicy.Lot(1L, 5, today.plusDays(10), now);
        FefoPolicy.Lot lot2 = new FefoPolicy.Lot(2L, 10, today.plusDays(20), now);

        List<FefoPolicy.Pick> plan = FefoPolicy.allocate(List.of(lot1, lot2), 8, today);

        assertEquals(2, plan.size());
        assertEquals(1L, plan.get(0).lotId());
        assertEquals(5, plan.get(0).quantity());
        assertEquals(2L, plan.get(1).lotId());
        assertEquals(3, plan.get(1).quantity());
    }

    @Test
    @DisplayName("FEFO: Bỏ qua các lô đã hết hạn sử dụng")
    void allocate_ShouldIgnoreExpiredLots() {
        FefoPolicy.Lot expiredLot = new FefoPolicy.Lot(1L, 10, today.minusDays(1), now);
        FefoPolicy.Lot validLot = new FefoPolicy.Lot(2L, 10, today.plusDays(10), now);

        List<FefoPolicy.Pick> plan = FefoPolicy.allocate(List.of(expiredLot, validLot), 5, today);

        assertEquals(1, plan.size());
        assertEquals(2L, plan.get(0).lotId());
        assertEquals(5, plan.get(0).quantity());
    }

    @Test
    @DisplayName("FEFO: Bỏ qua các lô có số lượng khả dụng bằng 0")
    void allocate_ShouldIgnoreZeroAvailableLots() {
        FefoPolicy.Lot zeroLot = new FefoPolicy.Lot(1L, 0, today.plusDays(5), now);
        FefoPolicy.Lot validLot = new FefoPolicy.Lot(2L, 10, today.plusDays(10), now);

        List<FefoPolicy.Pick> plan = FefoPolicy.allocate(List.of(zeroLot, validLot), 3, today);

        assertEquals(1, plan.size());
        assertEquals(2L, plan.get(0).lotId());
        assertEquals(3, plan.get(0).quantity());
    }

    @Test
    @DisplayName("FEFO: Lô không có hạn sử dụng (null) được xếp sau các lô có hạn")
    void allocate_WhenLotHasNullExpiry_ShouldPickLotWithExpiryFirst() {
        FefoPolicy.Lot nullExpiryLot = new FefoPolicy.Lot(1L, 10, null, now);
        FefoPolicy.Lot withExpiryLot = new FefoPolicy.Lot(2L, 10, today.plusDays(10), now);

        List<FefoPolicy.Pick> plan = FefoPolicy.allocate(List.of(nullExpiryLot, withExpiryLot), 5, today);

        assertEquals(1, plan.size());
        assertEquals(2L, plan.get(0).lotId());
        assertEquals(5, plan.get(0).quantity());
    }

    @Test
    @DisplayName("FEFO: Ném ngoại lệ khi tổng tồn kho còn hạn không đủ số lượng yêu cầu")
    void allocate_WhenInsufficientStock_ShouldThrowIllegalArgumentException() {
        FefoPolicy.Lot lot1 = new FefoPolicy.Lot(1L, 5, today.plusDays(10), now);
        FefoPolicy.Lot expiredLot = new FefoPolicy.Lot(2L, 10, today.minusDays(2), now);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FefoPolicy.allocate(List.of(lot1, expiredLot), 8, today));
        assertEquals("Không đủ hàng còn hạn trong các lô khả dụng", ex.getMessage());
    }

    @Test
    @DisplayName("FEFO: Ném ngoại lệ khi số lượng yêu cầu không dương")
    void allocate_WhenQuantityZeroOrNegative_ShouldThrowIllegalArgumentException() {
        List<FefoPolicy.Lot> lots = List.of(new FefoPolicy.Lot(1L, 10, today.plusDays(10), now));

        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class,
                () -> FefoPolicy.allocate(lots, 0, today));
        assertEquals("Số lượng phải dương", exZero.getMessage());

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class,
                () -> FefoPolicy.allocate(lots, -5, today));
        assertEquals("Số lượng phải dương", exNeg.getMessage());
    }
}
