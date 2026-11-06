package com.api.supermarket.service;
import com.api.supermarket.dto.request.LoginRequest;
import com.api.supermarket.dto.response.AuthMeResponse;
import com.api.supermarket.dto.response.LoginResponse;
public interface AuthService {
    // Phương thức để xử lý đăng nhập và trả về thông tin người dùng cùng với token
    LoginResponse login(LoginRequest request);
    // Phương thức để lấy thông tin người dùng hiện tại dựa trên tên đăng nhập
    AuthMeResponse me(String username);
}
