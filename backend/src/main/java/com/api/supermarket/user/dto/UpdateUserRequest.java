package com.api.supermarket.user.dto;

import com.api.supermarket.user.Role;
import com.api.supermarket.user.UserFields;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ADMIN sửa tài khoản. {@code newPassword} để trống thì giữ mật khẩu cũ. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 120) String email,
        @Pattern(regexp = UserFields.PHONE_PATTERN, message = "Số điện thoại 10–11 số, bắt đầu bằng 0") String phone,
        @NotNull Role role,
        @NotNull Boolean active,
        String newPassword) {
}
