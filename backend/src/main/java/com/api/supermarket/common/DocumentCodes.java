package com.api.supermarket.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Định dạng mã chứng từ: {@code <tiền tố><yyMMdd>-<số thứ tự, đệm 4 chữ số>}. */
public final class DocumentCodes {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private DocumentCodes() {
    }

    public static String format(DocumentType type, LocalDate day, long seq) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(day, "day");
        if (seq < 1) {
            throw new IllegalArgumentException("seq phải >= 1");
        }
        return type.prefix() + DAY.format(day) + "-" + String.format("%04d", seq);
    }

    /** Khóa của document trong collection {@code counters}, ví dụ {@code HD:261107}. */
    public static String counterKey(DocumentType type, LocalDate day) {
        return type.prefix() + ":" + DAY.format(day);
    }
}
