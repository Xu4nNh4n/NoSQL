package com.api.supermarket.catalog.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.api.supermarket.exception.BadRequestException;

class ProductAttributesTest {

    @Test
    void keepsScalarsAndDropsEmptyValues() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("dungTich", " 1L ");
        in.put("baoHanhThang", 12);
        in.put("huuCo", true);
        in.put("ghiChu", "");
        in.put("mau", null);
        assertEquals(Map.of("dungTich", "1L", "baoHanhThang", 12, "huuCo", true), ProductAttributes.validate(in));
    }

    @Test
    void rejectsOperatorAndDottedKeys() {
        assertThrows(BadRequestException.class, () -> ProductAttributes.validate(Map.of("$gt", "x")));
        assertThrows(BadRequestException.class, () -> ProductAttributes.validate(Map.of("a.b", "x")));
        assertThrows(BadRequestException.class, () -> ProductAttributes.validate(Map.of("1abc", "x")));
    }

    @Test
    void rejectsNestedObjects() {
        assertThrows(BadRequestException.class,
                () -> ProductAttributes.validate(Map.of("nested", Map.of("$where", "1"))));
    }

    @Test
    void limitsCountAndLength() {
        Map<String, Object> many = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            many.put("k" + i, i);
        }
        assertThrows(BadRequestException.class, () -> ProductAttributes.validate(many));
        assertThrows(BadRequestException.class, () -> ProductAttributes.validate(Map.of("moTa", "x".repeat(201))));
    }

    @Test
    void nullIsEmpty() {
        assertEquals(Map.of(), ProductAttributes.validate(null));
    }
}
