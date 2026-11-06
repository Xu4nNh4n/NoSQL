package com.api.supermarket.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.CreateUserRequest;
import com.api.supermarket.dto.request.UpdateUserRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.UserResponse;
import com.api.supermarket.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management - Quản lý nhân viên & Người dùng", description = "Internal employee and system user accounts administration - Quản trị tài khoản nhân viên và người dùng hệ thống")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Get all users - Lấy danh sách người dùng", description = "Paginate and filter users by keyword, role, name, email, phone, or active status - Phân trang và tìm kiếm người dùng theo vai trò, tên, email, SĐT")
    public PageResponse<UserResponse> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "userId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean employeesOnly) {
        return userService.filterUsers(page, size, sortBy, sortDir, keyword, roleName, fullName, email, phone, active, employeesOnly);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID - Lấy thông tin người dùng theo ID", description = "Get detailed information of a user account by ID - Xem thông tin chi tiết của tài khoản theo mã ID")
    public UserResponse getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "Create user - Tạo tài khoản người dùng mới", description = "Create a new employee/system user with assigned role - Tạo tài khoản nhân viên/người dùng mới với vai trò chỉ định")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "Update user - Cập nhật người dùng", description = "Update user profile details and role assignment - Cập nhật thông tin và vai trò của tài khoản")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user - Vô hiệu hóa tài khoản người dùng", description = "Soft delete / deactivate a user account - Xóa mềm (vô hiệu hóa) tài khoản người dùng")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

}
