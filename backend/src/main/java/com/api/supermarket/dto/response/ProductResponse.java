package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductResponse {
    private Long productId;
    private String productName;
    private String skuCode;
    private BigDecimal price;
    private Integer stockQuantity;
    private Integer damagedQuantity = 0;
    private Integer availableQuantity;
    private Integer reorderPoint;
    private String imageUrl;
    private Boolean isActive;
    private String categoryName;
    private LocalDateTime createAt;


    public ProductResponse(
        Long productId,
        String productName,
        String skuCode,
        BigDecimal price,
        Integer stockQuantity,
        Integer reorderPoint,
        String imageUrl,
        Boolean isActive,
        String categoryName,
        LocalDateTime createAt
    ) {
        this.productId = productId;
        this.productName = productName;
        this.skuCode = skuCode;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.reorderPoint = reorderPoint;
        this.imageUrl = imageUrl;
        this.isActive = isActive;
        this.categoryName = categoryName;
        this.createAt = createAt;
    }

    // Getters and Setters
    public Integer getAvailableQuantity() { return availableQuantity == null ? stockQuantity : availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public Integer getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(Integer damagedQuantity) { this.damagedQuantity = damagedQuantity; }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }



    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getReorderPoint() {
        return reorderPoint;
    }

    public void setReorderPoint(Integer reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

}
