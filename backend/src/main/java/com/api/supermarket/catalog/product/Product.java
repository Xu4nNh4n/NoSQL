package com.api.supermarket.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sản phẩm (collection {@code products}).
 * <ul>
 * <li>{@code attributes}: thuộc tính riêng từng ngành hàng (schema linh hoạt của MongoDB), ví dụ
 * {@code {"dungTich": "1L", "baoHanhThang": 12}}.</li>
 * <li>{@code categoryName}: chép từ nhóm hàng để danh sách không phải tra thêm.</li>
 * <li>{@code stockQuantity}, {@code damagedQuantity}: số tổng hợp, chỉ đổi cùng transaction với lô hàng.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@Document(Product.COLLECTION)
public class Product {
    public static final String COLLECTION = "products";

    @Id
    private String id;
    private String sku;
    private String name;
    private BigDecimal price;
    private String categoryId;
    private String categoryName;
    private String supplierId;
    private int reorderPoint;
    private String imageUrl;
    private boolean active;
    private Map<String, Object> attributes = new LinkedHashMap<>();
    private int stockQuantity;
    private int damagedQuantity;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;
}
