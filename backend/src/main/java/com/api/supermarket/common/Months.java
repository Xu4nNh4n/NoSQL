package com.api.supermarket.common;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import com.api.supermarket.exception.BadRequestException;

/** Đọc tham số tháng dạng {@code yyyy-MM}; để trống thì lấy tháng hiện tại theo múi giờ ứng dụng. */
public final class Months {
    private Months() {
    }

    public static YearMonth parseOrCurrent(String month, AppTime time) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(time.today());
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Tháng phải có dạng yyyy-MM, ví dụ 2026-11");
        }
    }
}
