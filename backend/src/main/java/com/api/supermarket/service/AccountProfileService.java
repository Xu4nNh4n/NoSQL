package com.api.supermarket.service;

import com.api.supermarket.dto.request.ChangePasswordRequest;
import com.api.supermarket.dto.request.UpdateProfileRequest;
import com.api.supermarket.dto.response.AccountProfileResponse;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.repository.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountProfileService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AccountProfileService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public AccountProfileResponse get(String username) {
        User user = active(users.findByUserName(username).orElse(null));
        return response(user);
    }

    @Transactional
    public AccountProfileResponse update(String username, UpdateProfileRequest request) {
        User user = active(users.findByUserNameForUpdate(username).orElse(null));
        String email = nullable(request.email());
        if (email != null) email = email.toLowerCase(Locale.ROOT);
        String phone = nullable(request.phone());
        if (email != null && users.existsByEmailIgnoreCaseAndUserIdNot(email, user.getUserId())) {
            throw new BadRequestException("Email đã được tài khoản khác sử dụng");
        }
        if (phone != null && users.existsByPhoneAndUserIdNot(phone, user.getUserId())) {
            throw new BadRequestException("Số điện thoại đã được tài khoản khác sử dụng");
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        return response(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = active(users.findByUserNameForUpdate(username).orElse(null));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Xác nhận mật khẩu mới không khớp");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu mới vượt giới hạn 72 byte; vui lòng nhập ngắn hơn");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
    }

    private User active(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Tài khoản không tồn tại hoặc đã bị khóa");
        }
        return user;
    }

    private AccountProfileResponse response(User user) {
        return new AccountProfileResponse(user.getUserId(), user.getUserName(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getRole().getRoleName(),
                null);
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
