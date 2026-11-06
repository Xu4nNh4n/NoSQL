package com.api.supermarket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ReturnItemRequest {
    @NotNull @Positive
    private Long orderItemId;
    @Positive
    private Long batchId;
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    @NotNull @Positive
    private Integer quantity;
    @NotNull
    private Boolean restock;
    @NotBlank @Size(max = 255)
    private String reason;

    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Boolean getRestock() { return restock; }
    public void setRestock(Boolean restock) { this.restock = restock; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
