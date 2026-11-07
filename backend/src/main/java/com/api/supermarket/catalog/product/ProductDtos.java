package com.api.supermarket.catalog.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ProductDtos {
    private ProductDtos() {
    }

    /**
     * Tạo hoặc sửa sản phẩm. {@code version} bắt buộc khi sửa. {@code stockQuantity},
     * {@code damagedQuantity} có mặt chỉ để từ chối rõ ràng: tồn kho đổi qua lô, bán, trả.
     */
    public record ProductRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{2,40}$", message = "2–40 ký tự: chữ, số, dấu chấm, gạch") String sku,
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0, message = "Giá VND không có phần lẻ") BigDecimal price,
            @NotBlank String categoryId,
            String supplierId,
            @Min(0) Integer reorderPoint,
            @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "Ảnh phải là địa chỉ http(s)") String imageUrl,
            Map<String, Object> attributes,
            Boolean active,
            Long version,
            Integer stockQuantity,
            Integer damagedQuantity) {
    }

    public record ProductResponse(String id, String sku, String name, BigDecimal price, String categoryId,
            String categoryName, String supplierId, int reorderPoint, String imageUrl, boolean active,
            Map<String, Object> attributes, int stockQuantity, int damagedQuantity, Instant createdAt,
            Instant updatedAt, Long version) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getCategoryId(),
                    p.getCategoryName(), p.getSupplierId(), p.getReorderPoint(), p.getImageUrl(), p.isActive(),
                    p.getAttributes(), p.getStockQuantity(), p.getDamagedQuantity(), p.getCreatedAt(),
                    p.getUpdatedAt(), p.getVersion());
        }
    }

    /** Bản rút gọn cho màn hình bán hàng, được cache trên Redis. */
    public record PosProduct(String id, String sku, String name, BigDecimal price, String categoryId,
            String categoryName, String imageUrl, int stockQuantity) {

        public static PosProduct from(Product p) {
            return new PosProduct(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getCategoryId(),
                    p.getCategoryName(), p.getImageUrl(), p.getStockQuantity());
        }
    }
}
