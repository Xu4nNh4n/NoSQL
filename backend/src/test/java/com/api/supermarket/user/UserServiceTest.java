package com.api.supermarket.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.api.supermarket.auth.RefreshTokenStore;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.user.dto.CreateUserRequest;
import com.api.supermarket.user.dto.UpdateUserRequest;
import com.api.supermarket.user.dto.UserResponse;

class UserServiceTest {
    private UserRepository users;
    private RefreshTokenStore tokens;
    private UserService service;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(RefreshTokenStore.class);
        AppTime time = new AppTime(Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        service = new UserService(users, new BCryptPasswordEncoder(4), tokens, time);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        admin = account("a1", Role.ADMIN);
        when(users.findById("a1")).thenReturn(Optional.of(admin));
    }

    private static UserAccount account(String id, Role role) {
        UserAccount u = new UserAccount();
        u.setId(id);
        u.setUsername(id);
        u.setFullName(id);
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    @Test
    void createNormalizesAndHashes() {
        UserResponse res = service.create(new CreateUserRequest("ThuNgan02", "matkhau123", " Thu ngân ", " B@Shop.vn ",
                "0901234567", Role.STAFF, null));
        assertEquals("thungan02", res.username());
        assertEquals("b@shop.vn", res.email());
        assertEquals("Thu ngân", res.fullName());
        assertTrue(res.active());
    }

    @Test
    void createRejectsDuplicates() {
        when(users.existsByUsername("thungan02")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create(
                new CreateUserRequest("thungan02", "matkhau123", "X", null, null, Role.STAFF, true)));

        when(users.existsByField("email", "a@shop.vn", null)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create(
                new CreateUserRequest("moi", "matkhau123", "X", "a@shop.vn", null, Role.STAFF, true)));
    }

    @Test
    void cannotLockYourself() {
        assertThrows(BadRequestException.class, () -> service.deactivate("a1", "a1"));
        assertThrows(BadRequestException.class, () -> service.update("a1",
                new UpdateUserRequest("a1", null, null, Role.STAFF, true, null), "a1"));
    }

    @Test
    void cannotLockOrDemoteLastActiveAdmin() {
        when(users.countActiveAdmins()).thenReturn(1L);
        assertThrows(BadRequestException.class, () -> service.deactivate("a1", "other"));
        assertThrows(BadRequestException.class, () -> service.update("a1",
                new UpdateUserRequest("a1", null, null, Role.WAREHOUSE, true, null), "other"));
        assertTrue(admin.isActive());
        verify(tokens, never()).revokeAll(anyString());
    }

    @Test
    void lockingRevokesSessions() {
        UserAccount staff = account("s1", Role.STAFF);
        when(users.findById("s1")).thenReturn(Optional.of(staff));
        service.deactivate("s1", "a1");
        assertFalse(staff.isActive());
        verify(tokens).revokeAll("s1");
    }

    @Test
    void plainProfileEditKeepsSessions() {
        UserAccount staff = account("s1", Role.STAFF);
        when(users.findById("s1")).thenReturn(Optional.of(staff));
        service.update("s1", new UpdateUserRequest("Tên mới", null, null, Role.STAFF, true, " "), "a1");
        assertEquals("Tên mới", staff.getFullName());
        verify(tokens, never()).revokeAll(anyString());
    }

    @Test
    void sortFieldIsWhitelisted() {
        assertThrows(BadRequestException.class,
                () -> service.list(0, 20, "passwordHash", "asc", null, null, null));
    }
}
