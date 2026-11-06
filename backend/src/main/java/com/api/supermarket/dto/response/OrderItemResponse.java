package com.api.supermarket.dto.response;

import java.math.BigDecimal;

public class OrderItemResponse {

    private Long orderItemId;
    private ProductResponse product;
    private Integer quantity;
    private Integer returnedQuantity = 0;
    private java.util.List<BatchAllocationResponse> batchAllocations = java.util.List.of();
    public java.util.List<BatchAllocationResponse> getBatchAllocations() { return batchAllocations; }
    public void setBatchAllocations(java.util.List<BatchAllocationResponse> allocations) { this.batchAllocations = allocations; }

    public Integer getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(Integer returnedQuantity) { this.returnedQuantity = returnedQuantity; }
    private BigDecimal unitPrice;
    private BigDecimal subTotal;
    private BigDecimal netUnitPrice;
    private com.api.supermarket.domain.PromotionSnapshot promotionSnapshot;
    public BigDecimal getOriginalUnitPrice() { return unitPrice; }
    public BigDecimal getNetUnitPrice() { return netUnitPrice == null ? unitPrice : netUnitPrice; }
    public void setNetUnitPrice(BigDecimal value) { netUnitPrice = value; }
    public com.api.supermarket.domain.PromotionSnapshot getPromotionSnapshot() { return promotionSnapshot; }
    public void setPromotionSnapshot(com.api.supermarket.domain.PromotionSnapshot value) { promotionSnapshot = value; }
    public BigDecimal getGrossSubtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    public BigDecimal getDiscountAmount() { return getGrossSubtotal().subtract(subTotal); }

    public OrderItemResponse() {
    }

    public OrderItemResponse(Long orderItemId, ProductResponse product, Integer quantity, BigDecimal unitPrice,
            BigDecimal subTotal) {
        this.orderItemId = orderItemId;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subTotal = subTotal;
    }

    // Getters and Setters
    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public ProductResponse getProduct() {
        return product;
    }

    public void setProduct(ProductResponse product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubTotal() {
        return subTotal;
    }

    public void setSubTotal(BigDecimal subTotal) {
        this.subTotal = subTotal;
    }

}
