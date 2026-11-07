package com.api.supermarket.auth;

import java.nio.charset.StandardCharsets;

import com.api.supermarket.exception.BadRequestException;

/** BCrypt chỉ dùng 72 byte đầu, nên mật khẩu dài hơn bị từ chối thay vì bị cắt ngầm (SRS 2.6). */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BadRequestException("Mật khẩu phải có ít nhất " + MIN_LENGTH + " ký tự");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("Mật khẩu tối đa " + MAX_BYTES + " byte");
        }
    }
}
