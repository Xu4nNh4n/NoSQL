package com.api.supermarket.auth.dto;

import com.api.supermarket.user.UserFields;
import com.api.supermarket.user.dto.UserResponse;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response của /api/auth. */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /**
     * @param refreshToken null khi Redis tạm thời không khả dụng: phiên chỉ kéo dài tới khi access token hết hạn
     * @param expiresIn    số giây access token còn hiệu lực
     */
    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Email @Size(max = 120) String email,
            @Pattern(regexp = UserFields.PHONE_PATTERN, message = "Số điện thoại 10–11 số, bắt đầu bằng 0") String phone) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword,
            @NotBlank String confirmPassword) {
    }
}
