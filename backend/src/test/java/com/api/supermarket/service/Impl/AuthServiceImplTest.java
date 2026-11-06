package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.api.supermarket.dto.request.LoginRequest;
import com.api.supermarket.dto.response.LoginResponse;
import com.api.supermarket.dto.response.AuthMeResponse;
import com.api.supermarket.entity.Role;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.security.JwtService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private User createSampleUser() {
        Role role = new Role();
        role.setRoleId(1L);
        role.setRoleName("ROLE_ADMIN");

        User user = new User();
        user.setUserId(1L);
        user.setUserName("admin");
        user.setPasswordHash("hashedPassword");
        user.setFullName("Nguyễn Quản Trị");
        user.setEmail("admin@supermarket.com");
        user.setPhone("0987654321");
        user.setIsActive(true);
        user.setRole(role);
        return user;
    }

    @Test
    void login_Success() {
        User user = createSampleUser();
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Password@123");

        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mockAccessToken");

        LoginResponse response = authService.login(req);

        assertNotNull(response);
        assertEquals("admin", response.getUsername());
        assertEquals("ROLE_ADMIN", response.getRole());
        assertEquals("mockAccessToken", response.getAccessToken());
    }

    @Test
    void login_UserNotFound_ThrowsUnauthorized() {
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent");
        req.setPassword("password");

        when(userRepository.findByUserName("nonexistent")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req));
        assertEquals("Tên đăng nhập hoặc mật khẩu không đúng", ex.getMessage());
    }

    @Test
    void login_UserInactive_ThrowsUnauthorized() {
        User user = createSampleUser();
        user.setIsActive(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Password@123");

        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req));
        assertEquals("Tài khoản đã bị khóa", ex.getMessage());
    }

    @Test
    void login_WrongPassword_ThrowsUnauthorized() {
        User user = createSampleUser();
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrongPassword");

        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(req));
        assertEquals("Tên đăng nhập hoặc mật khẩu không đúng", ex.getMessage());
    }


    @Test
    void me_Success() {
        User user = createSampleUser();
        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));

        AuthMeResponse response = authService.me("admin");

        assertNotNull(response);
        assertEquals("admin", response.getUsername());
        assertEquals("Nguyễn Quản Trị", response.getFullName());
        assertEquals("ROLE_ADMIN", response.getRole());
    }

    @Test
    void me_InactiveUser_ThrowsUnauthorized() {
        User user = createSampleUser();
        user.setIsActive(false);
        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.me("admin"));
    }
}
