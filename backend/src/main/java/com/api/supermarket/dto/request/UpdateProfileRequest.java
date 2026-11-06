package com.api.supermarket.dto.request;

import jakarta.validation.constraints.*;

public record UpdateProfileRequest(
        @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 150) String fullName,
        @Email(message = "Email không hợp lệ") @Size(max = 100) String email,
        @Pattern(regexp = "^$|\\+?[0-9]{9,15}", message = "Số điện thoại cần 9–15 chữ số") String phone,
        @Size(max = 255) String address) {}
