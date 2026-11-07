package com.api.supermarket.user.dto;

import java.time.Instant;

import com.api.supermarket.user.Role;
import com.api.supermarket.user.UserAccount;

/** Thông tin tài khoản trả cho client; không bao giờ có mật khẩu băm. */
public record UserResponse(String id, String username, String fullName, String email, String phone, Role role,
        boolean active, Instant createdAt) {

    public static UserResponse from(UserAccount u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.isActive(), u.getCreatedAt());
    }
}
