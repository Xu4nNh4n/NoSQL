package com.api.supermarket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.api.supermarket.auth.dto.AuthDtos.ChangePasswordRequest;
import com.api.supermarket.auth.dto.AuthDtos.TokenResponse;
import com.api.supermarket.auth.dto.AuthDtos.UpdateProfileRequest;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.security.JwtService;
import com.api.supermarket.user.Role;
import com.api.supermarket.user.UserAccount;
import com.api.supermarket.user.UserRepository;
import com.api.supermarket.user.UserService;

class AuthServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    // cost 4 cho test nhanh
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AppTime time = new AppTime(Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private final JwtService jwt = new JwtService(SECRET, 900_000);

    private UserRepository users;
    private RefreshTokenStore tokens;
    private LoginHistoryStore history;
    private AuthService auth;
    private UserAccount staff;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(RefreshTokenStore.class);
        history = mock(LoginHistoryStore.class);
        UserService userService = new UserService(users, encoder, tokens, time);
        auth = new AuthService(users, userService, encoder, jwt, tokens, history, time);

        staff = new UserAccount();
        staff.setId("u1");
        staff.setUsername("thungan01");
        staff.setPasswordHash(encoder.encode("matkhau123"));
        staff.setFullName("Thu ngân A");
        staff.setRole(Role.STAFF);
        staff.setActive(true);
        when(users.findByUsername("thungan01")).thenReturn(Optional.of(staff));
        when(users.findById("u1")).thenReturn(Optional.of(staff));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void loginIssuesTokensAndRecordsSuccess() {
        when(tokens.issue("u1")).thenReturn(Optional.of("refresh-1"));
        TokenResponse res = auth.login(" ThuNgan01 ", "matkhau123", "1.2.3.4", "Chrome");

        assertEquals("refresh-1", res.refreshToken());
        assertEquals(900, res.expiresIn());
        assertEquals(new JwtPrincipal("u1", "thungan01", "STAFF"), jwt.parse(res.accessToken()).orElseThrow());
        verify(history).record("u1", true, "1.2.3.4", "Chrome");
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        UnauthorizedException wrong = assertThrows(UnauthorizedException.class,
                () -> auth.login("thungan01", "sai-mat-khau", "ip", "ua"));
        UnauthorizedException unknown = assertThrows(UnauthorizedException.class,
                () -> auth.login("khongco", "sai-mat-khau", "ip", "ua"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        verify(history).record("u1", false, "ip", "ua");
        verify(tokens, never()).issue(anyString());
    }

    @Test
    void lockedAccountIsRejectedAfterCorrectPassword() {
        staff.setActive(false);
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> auth.login("thungan01", "matkhau123", "ip", "ua"));
        assertEquals(AuthService.LOCKED, ex.getMessage());
    }

    @Test
    void loginStillWorksWhenRedisIsDown() {
        when(tokens.issue("u1")).thenReturn(Optional.empty());
        TokenResponse res = auth.login("thungan01", "matkhau123", "ip", "ua");
        assertNotNull(res.accessToken());
        assertNull(res.refreshToken());
    }

    @Test
    void refreshRotatesToken() {
        when(tokens.consume("old")).thenReturn(Optional.of("u1"));
        when(tokens.issue("u1")).thenReturn(Optional.of("new"));
        assertEquals("new", auth.refresh("old").refreshToken());
        verify(tokens).consume("old");
    }

    @Test
    void refreshWithUnknownTokenIs401() {
        when(tokens.consume("x")).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> auth.refresh("x"));
    }

    @Test
    void refreshForLockedUserIs401() {
        staff.setActive(false);
        when(tokens.consume("old")).thenReturn(Optional.of("u1"));
        assertThrows(UnauthorizedException.class, () -> auth.refresh("old"));
        verify(tokens, never()).issue(anyString());
    }

    @Test
    void changePasswordRules() {
        JwtPrincipal me = new JwtPrincipal("u1", "thungan01", "STAFF");
        assertThrows(BadRequestException.class,
                () -> auth.changePassword(me, new ChangePasswordRequest("sai", "matkhaumoi1", "matkhaumoi1")));
        assertThrows(BadRequestException.class,
                () -> auth.changePassword(me, new ChangePasswordRequest("matkhau123", "matkhaumoi1", "khac")));
        assertThrows(BadRequestException.class,
                () -> auth.changePassword(me, new ChangePasswordRequest("matkhau123", "matkhau123", "matkhau123")));
        verify(tokens, never()).revokeAll(anyString());

        auth.changePassword(me, new ChangePasswordRequest("matkhau123", "matkhaumoi1", "matkhaumoi1"));
        assertEquals(true, encoder.matches("matkhaumoi1", staff.getPasswordHash()));
        verify(tokens).revokeAll("u1");
    }

    @Test
    void profileUpdateNormalizesEmailAndRejectsDuplicates() {
        JwtPrincipal me = new JwtPrincipal("u1", "thungan01", "STAFF");
        auth.updateProfile(me, new UpdateProfileRequest("Thu ngân B", " A@Shop.VN ", ""));
        assertEquals("a@shop.vn", staff.getEmail());
        assertNull(staff.getPhone());

        when(users.existsByField("email", "b@shop.vn", "u1")).thenReturn(true);
        assertThrows(BadRequestException.class,
                () -> auth.updateProfile(me, new UpdateProfileRequest("X", "b@shop.vn", null)));
    }
}
