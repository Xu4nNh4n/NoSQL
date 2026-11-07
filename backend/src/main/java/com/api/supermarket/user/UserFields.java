package com.api.supermarket.user;

import java.util.Locale;

/** Chuẩn hóa trường người dùng trước khi lưu và so trùng. Chuỗi rỗng thành null để không ghi xuống document. */
public final class UserFields {
    public static final String USERNAME_PATTERN = "^[A-Za-z0-9._]{3,30}$";
    public static final String PHONE_PATTERN = "^(0\\d{9,10})?$";

    private UserFields() {
    }

    public static String username(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String email(String value) {
        return blankToNull(value == null ? null : value.trim().toLowerCase(Locale.ROOT));
    }

    public static String phone(String value) {
        return blankToNull(value == null ? null : value.trim());
    }

    public static String text(String value) {
        return blankToNull(value == null ? null : value.trim());
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
