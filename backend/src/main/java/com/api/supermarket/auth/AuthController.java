package com.api.supermarket.auth;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.auth.LoginHistoryStore.LoginEvent;
import com.api.supermarket.auth.dto.AuthDtos.ChangePasswordRequest;
import com.api.supermarket.auth.dto.AuthDtos.LoginRequest;
import com.api.supermarket.auth.dto.AuthDtos.RefreshRequest;
import com.api.supermarket.auth.dto.AuthDtos.TokenResponse;
import com.api.supermarket.auth.dto.AuthDtos.UpdateProfileRequest;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.Months;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.user.dto.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AppTime time;

    public AuthController(AuthService auth, AppTime time) {
        this.auth = auth;
        this.time = time;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req.username(), req.password(), http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return auth.me(principal);
    }

    @PutMapping("/me")
    public UserResponse updateProfile(@AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req) {
        return auth.updateProfile(principal, req);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(principal, req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/logins")
    public List<LoginEvent> myLogins(@AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String month) {
        return auth.loginHistory(principal.userId(), Months.parseOrCurrent(month, time));
    }
}
