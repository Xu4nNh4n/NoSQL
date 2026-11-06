package com.api.supermarket.dto.response;

import java.time.LocalDateTime;

public class UserResponse {
    private Long userId;
    private String fullName;
    private String userName;
    private String email;
    private String phone;
    private String roleName;
    private Boolean isActive;
    private LocalDateTime createAt;

    public UserResponse() {
    }

    public UserResponse(
        Long userId,
        String fullName,
        String userName,
        String email,
        String phone,
        String roleName,
        Boolean isActive,
        LocalDateTime createAt
    ) {
        this.userId = userId;
        this.fullName = fullName;
        this.userName = userName;
        this.email = email;
        this.phone = phone;
        this.roleName = roleName;
        this.isActive = isActive;
        this.createAt = createAt;
    }

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }
    
}
