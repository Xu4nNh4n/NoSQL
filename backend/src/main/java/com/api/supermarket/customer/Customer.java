package com.api.supermarket.customer;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Khách hàng tại quầy (collection {@code customers}); không có tài khoản đăng nhập. Email lưu chữ thường. */
@Data
@NoArgsConstructor
@Document(Customer.COLLECTION)
public class Customer {
    public static final String COLLECTION = "customers";

    @Id
    private String id;
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;
}
