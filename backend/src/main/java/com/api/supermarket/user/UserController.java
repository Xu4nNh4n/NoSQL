package com.api.supermarket.user;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.auth.AuthService;
import com.api.supermarket.auth.LoginHistoryStore.LoginEvent;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.Months;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.user.dto.CreateUserRequest;
import com.api.supermarket.user.dto.UpdateUserRequest;
import com.api.supermarket.user.dto.UserResponse;

import jakarta.validation.Valid;

/** Quản lý người dùng, chỉ ADMIN (FR-02, NFR-02). */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    public record RoleResponse(Role name, String description) {
    }

    private final UserService users;
    private final AuthService auth;
    private final AppTime time;

    public UserController(UserService users, AuthService auth, AppTime time) {
        this.users = users;
        this.auth = auth;
        this.time = time;
    }

    @GetMapping("/users")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active) {
        return users.list(page, size, sortBy, sortDir, keyword, role, active);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return users.create(req);
    }

    @GetMapping("/users/{id}")
    public UserResponse get(@PathVariable String id) {
        return users.get(id);
    }

    @PutMapping("/users/{id}")
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest req,
            @AuthenticationPrincipal JwtPrincipal actor) {
        return users.update(id, req, actor.userId());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id, @AuthenticationPrincipal JwtPrincipal actor) {
        users.deactivate(id, actor.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/logins")
    public List<LoginEvent> logins(@PathVariable String id, @RequestParam(required = false) String month) {
        users.load(id);
        return auth.loginHistory(id, Months.parseOrCurrent(month, time));
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return Arrays.stream(Role.values()).map(r -> new RoleResponse(r, r.description())).toList();
    }
}
