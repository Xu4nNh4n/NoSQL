package com.api.supermarket.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.response.RoleResponse;
import com.api.supermarket.service.RoleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles - Vai trò hệ thống", description = "Danh sách vai trò cố định để gán cho tài khoản người dùng")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Get all roles - Lấy danh sách vai trò", description = "Retrieve list of all roles in the system - Lấy danh sách toàn bộ các vai trò trong hệ thống")
    public List<RoleResponse> getAllRoles() {
        return roleService.getALlRoles();
    }

}
