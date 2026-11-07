package com.api.supermarket.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class DocumentCodesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    @Test
    void formatsEveryDocumentType() {
        assertEquals("HD261107-0042", DocumentCodes.format(DocumentType.ORDER, DAY, 42));
        assertEquals("PN261107-0001", DocumentCodes.format(DocumentType.PURCHASE_ORDER, DAY, 1));
        assertEquals("L261107-0003", DocumentCodes.format(DocumentType.BATCH, DAY, 3));
        assertEquals("PT261108-0001", DocumentCodes.format(DocumentType.RETURN, DAY.plusDays(1), 1));
    }

    @Test
    void keepsAllDigitsAbove9999() {
        assertEquals("HD261107-10000", DocumentCodes.format(DocumentType.ORDER, DAY, 10_000));
    }

    @Test
    void firstDayOfYear() {
        assertEquals("HD260101-0001", DocumentCodes.format(DocumentType.ORDER, LocalDate.of(2026, 1, 1), 1));
    }

    @Test
    void rejectsNonPositiveSequence() {
        assertThrows(IllegalArgumentException.class, () -> DocumentCodes.format(DocumentType.ORDER, DAY, 0));
    }

    @Test
    void counterKeyIsPerTypeAndDay() {
        assertEquals("HD:261107", DocumentCodes.counterKey(DocumentType.ORDER, DAY));
        assertEquals("L:261107", DocumentCodes.counterKey(DocumentType.BATCH, DAY));
    }
}
