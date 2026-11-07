package com.api.supermarket.inventory;

import static com.api.supermarket.inventory.InventoryTestSupport.TIME;
import static com.api.supermarket.inventory.InventoryTestSupport.TODAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.api.supermarket.common.RetryableConflictException;
import com.api.supermarket.inventory.FefoAllocator.Allocation;

class FefoAllocatorTest {
    private BatchRepository batches;
    private FefoAllocator allocator;

    @BeforeEach
    void setUp() {
        batches = mock(BatchRepository.class);
        allocator = new FefoAllocator(batches, TIME);
        when(batches.findAvailableForFefo("p1", TODAY)).thenReturn(List.of(
                InventoryTestSupport.batch("late", "p1", 50, TODAY.plusDays(20)),
                InventoryTestSupport.batch("soon", "p1", 30, TODAY.plusDays(5))));
    }

    @Test
    void takesEarliestExpiryFirstAndSplits() {
        when(batches.decrementIfEnough(anyString(), anyInt())).thenReturn(true);
        List<Allocation> result = allocator.allocate("p1", "Sữa", 35);
        assertEquals(List.of(
                new Allocation("soon", "L-soon", TODAY.plusDays(5), 30),
                new Allocation("late", "L-late", TODAY.plusDays(20), 5)), result);
        verify(batches).decrementIfEnough("soon", 30);
        verify(batches).decrementIfEnough("late", 5);
    }

    @Test
    void insufficientStockRejectsWholeRequestWithoutTouchingBatches() {
        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> allocator.allocate("p1", "Sữa", 81));
        assertEquals("Không đủ hàng còn hạn cho Sữa: cần 81, còn 80", ex.getMessage());
        verify(batches, never()).decrementIfEnough(anyString(), anyInt());
    }

    @Test
    void lostRaceAsksForRetry() {
        when(batches.decrementIfEnough("soon", 10)).thenReturn(false);
        assertThrows(RetryableConflictException.class, () -> allocator.allocate("p1", "Sữa", 10));
    }
}
