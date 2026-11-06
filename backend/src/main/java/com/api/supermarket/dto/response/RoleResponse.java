package com.api.supermarket.dto.response;

import java.time.LocalDateTime;

public class RoleResponse {
    private Long roleId;
    private String roleName;
    private String description;
    private LocalDateTime createAt;

    public RoleResponse(Long roleId, String roleName, String description, LocalDateTime createAt) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.description = description;
        this.createAt = createAt;
    }

    //getters and setters

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    
}
