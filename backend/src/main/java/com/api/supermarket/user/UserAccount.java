package com.api.supermarket.user;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tài khoản nội bộ (collection {@code users}). Email lưu chữ thường; trường rỗng không được ghi
 * xuống document, nhờ vậy index unique một phần trên email/phone bỏ qua người không có giá trị.
 */
@Data
@NoArgsConstructor
@Document(UserAccount.COLLECTION)
public class UserAccount {
    public static final String COLLECTION = "users";

    @Id
    private String id;
    private String username;
    private String passwordHash;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    /** Khóa lạc quan: hai người sửa cùng lúc thì người sau nhận 409. */
    @Version
    private Long version;
}
