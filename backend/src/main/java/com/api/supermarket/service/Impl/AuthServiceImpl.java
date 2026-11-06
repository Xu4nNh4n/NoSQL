package com.api.supermarket.service.Impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.api.supermarket.dto.request.LoginRequest;
import com.api.supermarket.dto.response.AuthMeResponse;
import com.api.supermarket.dto.response.LoginResponse;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.security.JwtService;
import com.api.supermarket.service.AuthService;
import com.api.supermarket.entity.*;
import com.api.supermarket.exception.UnauthorizedException;
@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    public AuthServiceImpl(
        UserRepository userRepository, 
        JwtService jwtService, 
        PasswordEncoder passwordEncoder){
        
            this.userRepository = userRepository;
            this.jwtService = jwtService;
            this.passwordEncoder = passwordEncoder;
    }

    // note: Login chi lam 3 viec chinh:
    // 1. Tim user theo username.
    // 2. Kiem tra trang thai tai khoan va password BCrypt.
    // 3. Tao JWT token neu thong tin dang nhap hop le.
    @Override
    public LoginResponse login(LoginRequest request){
        // note: Khong noi ro username sai hay password sai de tranh lo thong tin tai khoan.
        User user = userRepository.findByUserName(request.getUsername()).orElseThrow(() -> new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không đúng"));

        // note: User bi khoa thi khong cho dang nhap, du password dung.
        if(!Boolean.TRUE.equals(user.getIsActive())){
            throw new UnauthorizedException("Tài khoản đã bị khóa");
        }

    // note: request.getPassword() la password nguoi dung nhap.
    // user.getPasswordHash() la BCrypt hash trong database.
    if(!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())){
        throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không đúng");
    }

    // Chỉ phát JWT sau khi tài khoản và mật khẩu đều hợp lệ.
    String accessToken = jwtService.generateToken(user);

    // note: Dang nhap thanh cong thi phat token cho client.
    // Client se gui token nay trong header Authorization o cac request sau.
        return new LoginResponse(
            user.getUserId(),
            user.getUserName(),
            user.getRole().getRoleName(),
            "Đăng nhập thành công",
            accessToken
        );
    }

    @Override
    public AuthMeResponse me(String username){
        User user = userRepository.findByUserName(username).orElseThrow(() -> new UnauthorizedException("Người dùng không tồn tại"));
        if(!Boolean.TRUE.equals(user.getIsActive())){
            throw new UnauthorizedException("Người dùng không tồn tại hoặc đã bị khóa");
        }
        return new AuthMeResponse(user.getUserId(), user.getUserName(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getRole().getRoleName());
    }
}
