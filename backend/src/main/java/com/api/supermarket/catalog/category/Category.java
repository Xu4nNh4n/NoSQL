package com.api.supermarket.catalog.category;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Nhóm hàng (collection {@code categories}). {@code nameLower} dùng để chặn trùng tên không phân biệt hoa thường. */
@Data
@NoArgsConstructor
@Document(Category.COLLECTION)
public class Category {
    public static final String COLLECTION = "categories";

    @Id
    private String id;
    private String name;
    private String nameLower;
    private String description;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
