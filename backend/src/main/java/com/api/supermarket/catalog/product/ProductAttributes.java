package com.api.supermarket.catalog.product;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.api.supermarket.exception.BadRequestException;

/**
 * Kiểm tra thuộc tính linh hoạt trước khi ghi xuống MongoDB. Khóa không được có {@code $} hay {@code .}
 * (chặn chèn toán tử, NFR-11); giá trị chỉ là chuỗi, số hoặc đúng/sai, không lồng object.
 */
public final class ProductAttributes {
    public static final int MAX_ENTRIES = 20;
    public static final int MAX_TEXT = 200;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,39}$");

    private ProductAttributes() {
    }

    public static Map<String, Object> validate(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input == null) {
            return result;
        }
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object value = e.getValue();
            if (value == null || (value instanceof String s && s.isBlank())) {
                continue;
            }
            if (!KEY.matcher(e.getKey()).matches()) {
                throw new BadRequestException("Tên thuộc tính không hợp lệ: " + e.getKey()
                        + " (chữ cái đầu, chỉ gồm chữ, số, gạch dưới, tối đa 40 ký tự)");
            }
            if (value instanceof String s) {
                if (s.length() > MAX_TEXT) {
                    throw new BadRequestException("Giá trị thuộc tính " + e.getKey() + " tối đa " + MAX_TEXT + " ký tự");
                }
                result.put(e.getKey(), s.trim());
            } else if (value instanceof Number || value instanceof Boolean) {
                result.put(e.getKey(), value);
            } else {
                throw new BadRequestException("Thuộc tính " + e.getKey() + " chỉ nhận chuỗi, số hoặc đúng/sai");
            }
        }
        if (result.size() > MAX_ENTRIES) {
            throw new BadRequestException("Tối đa " + MAX_ENTRIES + " thuộc tính");
        }
        return result;
    }
}
