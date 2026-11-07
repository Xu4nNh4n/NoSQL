package com.api.supermarket.catalog.supplier;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Nhà cung cấp (collection {@code suppliers}). */
@Data
@NoArgsConstructor
@Document(Supplier.COLLECTION)
public class Supplier {
    public static final String COLLECTION = "suppliers";

    @Id
    private String id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
