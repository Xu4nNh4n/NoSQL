package com.api.supermarket.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 150, message = "Tên sản phẩm tối đa 150 ký tự")
    private String productName;

    @NotBlank(message = "Mã sản phẩm không được để trống")
    @Size(max = 50, message = "Mã sản phẩm tối đa 50 ký tự")
    private String skuCode;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0")
    private BigDecimal price;

    @NotNull(message = "Số lượng sản phẩm không được để trống")
    @Min(value = 0, message = "Số lượng sản phẩm phải lớn hơn hoặc bằng 0")
    private Integer stockQuantity;

    @Min(value = 0, message = "Ngưỡng cảnh báo tồn kho phải lớn hơn hoặc bằng 0")
    private Integer reorderPoint;

    private String imageUrl;

    private Boolean isActive = true;

    @NotBlank(message = "Tên nhóm hàng không được để trống")
    @Size(max = 100)
    private String categoryName;

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

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
}
