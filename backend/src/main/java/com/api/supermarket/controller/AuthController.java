package com.api.supermarket.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.LoginRequest;
import com.api.supermarket.dto.response.AccountProfileResponse;
import com.api.supermarket.dto.request.UpdateProfileRequest;
import com.api.supermarket.dto.request.ChangePasswordRequest;
import com.api.supermarket.service.AccountProfileService;
import com.api.supermarket.dto.response.LoginResponse;
import com.api.supermarket.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication - Xác thực", description = "Đăng nhập bằng JWT và quản lý hồ sơ nội bộ")
public class AuthController {
    private final AuthService authService;
    private final AccountProfileService profiles;

    public AuthController(AuthService authService, AccountProfileService profiles) {
        this.authService = authService;
        this.profiles = profiles;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập và cấp JWT; hết hạn cần đăng nhập lại")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info - Xem thông tin tài khoản hiện tại", description = "Retrieve profile details of the currently logged-in user - Lấy thông tin cá nhân của người dùng hiện đang đăng nhập")
    public AccountProfileResponse me(Authentication authentication) {
        return profiles.get(authentication.getName());
    }

    @PutMapping("/me")
    @Operation(summary = "Cập nhật thông tin tài khoản đang đăng nhập")
    public AccountProfileResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(authentication.getName(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Đổi mật khẩu bằng mật khẩu hiện tại")
    public java.util.Map<String, String> changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(authentication.getName(), request);
        return java.util.Map.of("message", "Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
    }
}
