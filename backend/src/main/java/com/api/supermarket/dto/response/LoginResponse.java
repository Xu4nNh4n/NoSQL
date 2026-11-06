package com.api.supermarket.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private String role;
    private String message; // thông báo đăng nhập thành công
    private String accessToken; // JWT sống ngắn, dùng để gọi các API nghiệp vụ.
}
