package com.api.supermarket.security;

/**
 * Người dùng đã xác thực, lấy từ access token.
 *
 * @param userId   định danh người dùng (chuỗi ObjectId của MongoDB)
 * @param username tên đăng nhập
 * @param role     ADMIN, WAREHOUSE hoặc STAFF
 */
public record JwtPrincipal(String userId, String username, String role) {
}
