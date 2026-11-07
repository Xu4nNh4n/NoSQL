package com.api.supermarket.auth;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.api.supermarket.auth.LoginHistoryStore.LoginEvent;
import com.api.supermarket.auth.dto.AuthDtos.ChangePasswordRequest;
import com.api.supermarket.auth.dto.AuthDtos.TokenResponse;
import com.api.supermarket.auth.dto.AuthDtos.UpdateProfileRequest;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.security.JwtService;
import com.api.supermarket.user.UserAccount;
import com.api.supermarket.user.UserFields;
import com.api.supermarket.user.UserRepository;
import com.api.supermarket.user.UserService;
import com.api.supermarket.user.dto.UserResponse;

/**
 * Tài khoản và phiên đăng nhập (FR-01): MongoDB giữ tài khoản, Redis giữ refresh token,
 * Cassandra giữ lịch sử đăng nhập.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String BAD_CREDENTIALS = "Tên đăng nhập hoặc mật khẩu không đúng";
    static final String LOCKED = "Tài khoản đã bị khóa";
    static final String SESSION_EXPIRED = "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại";

    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenStore tokens;
    private final LoginHistoryStore history;
    private final AppTime time;
    /** Băm giả để tên đăng nhập không tồn tại cũng mất thời gian BCrypt như tên có thật. */
    private final String dummyHash;

    public AuthService(UserRepository users, UserService userService, PasswordEncoder encoder, JwtService jwt,
            RefreshTokenStore tokens, LoginHistoryStore history, AppTime time) {
        this.users = users;
        this.userService = userService;
        this.encoder = encoder;
        this.jwt = jwt;
        this.tokens = tokens;
        this.history = history;
        this.time = time;
        this.dummyHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    public TokenResponse login(String username, String password, String ip, String userAgent) {
        Optional<UserAccount> found = users.findByUsername(UserFields.username(username));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        UserAccount user = found.get();
        if (!encoder.matches(password, user.getPasswordHash())) {
            history.record(user.getId(), false, ip, userAgent);
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            history.record(user.getId(), false, ip, userAgent);
            throw new UnauthorizedException(LOCKED);
        }
        history.record(user.getId(), true, ip, userAgent);
        return issueTokens(user);
    }

    /** Xoay vòng: token cũ bị xóa ngay khi dùng, rồi cấp cặp token mới với vai trò mới nhất từ MongoDB. */
    public TokenResponse refresh(String refreshToken) {
        String userId = tokens.consume(refreshToken).orElseThrow(() -> new UnauthorizedException(SESSION_EXPIRED));
        UserAccount user = users.findById(userId).filter(UserAccount::isActive)
                .orElseThrow(() -> new UnauthorizedException(SESSION_EXPIRED));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        tokens.revoke(refreshToken);
    }

    public UserResponse me(JwtPrincipal principal) {
        return UserResponse.from(userService.load(principal.userId()));
    }

    public UserResponse updateProfile(JwtPrincipal principal, UpdateProfileRequest req) {
        UserAccount user = userService.load(principal.userId());
        String email = UserFields.email(req.email());
        String phone = UserFields.phone(req.phone());
        userService.ensureContactUnique(email, phone, user.getId());
        user.setFullName(UserFields.text(req.fullName()));
        user.setEmail(email);
        user.setPhone(phone);
        user.setUpdatedAt(time.now());
        return UserResponse.from(users.save(user));
    }

    /** Đổi mật khẩu thì thu hồi mọi phiên, người dùng phải đăng nhập lại. */
    public void changePassword(JwtPrincipal principal, ChangePasswordRequest req) {
        UserAccount user = userService.load(principal.userId());
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new BadRequestException("Xác nhận mật khẩu không khớp");
        }
        PasswordPolicy.check(req.newPassword());
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu cũ");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setUpdatedAt(time.now());
        users.save(user);
        tokens.revokeAll(user.getId());
    }

    public List<LoginEvent> loginHistory(String userId, YearMonth month) {
        return history.list(userId, month);
    }

    private TokenResponse issueTokens(UserAccount user) {
        String access = jwt.generateToken(new JwtPrincipal(user.getId(), user.getUsername(), user.getRole().name()));
        String refresh = tokens.issue(user.getId()).orElse(null);
        if (refresh == null) {
            log.warn("Redis không khả dụng: {} chỉ nhận access token, không có refresh token", user.getUsername());
        }
        return new TokenResponse(access, refresh, jwt.expiresInSeconds(), UserResponse.from(user));
    }
}
