package com.api.supermarket.dto.request;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
        @NotBlank @Size(min = 8, max = 72, message = "Mật khẩu mới cần từ 8 đến 72 ký tự") String newPassword,
        @NotBlank(message = "Vui lòng xác nhận mật khẩu mới") String confirmPassword) {}
