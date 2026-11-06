package com.api.supermarket.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data // Tạo các phương thức getter, setter, toString, equals và hashCode
@AllArgsConstructor // Tạo constructor với tất cả các trường
public class AuthMeResponse {
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String role;
}
