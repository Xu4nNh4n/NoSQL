package com.api.supermarket.dto.response;

public record AccountProfileResponse(Long userId, String username, String fullName,
        String email, String phone, String role, String address) {}
