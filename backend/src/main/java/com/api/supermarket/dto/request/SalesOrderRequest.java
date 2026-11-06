package com.api.supermarket.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class SalesOrderRequest {

    @Size(max = 150)
    private String customerName;
    @Size(max = 20)
    private String customerPhone;

    @NotBlank(message = "Nơi thanh toán không được để trống")
    @Size(max = 20, message = "Nơi thanh toán tối đa 20 kí tự")
    private String orderType;

    @NotBlank(message = "Phương thức thanh toán không được để trống")
    @Size(max = 50, message = "Phương thức thanh toán tối đa 50 kí tự")
    private String paymentMethod;

    @Size(max = 100)
    private String paymentReference;

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    @Size(max = 255, message = "Ghi chú tối đa 255 kí tự")
    private String note;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    @Valid // ⚠️ RẤT QUAN TRỌNG: Kích hoạt kiểm tra validation cho từng món bên trong
           // (@NotNull, @Min(1))
    private List<OrderItemRequest> items;

    @NotBlank(message = "Cần báo giá POS trước khi thanh toán")
    @Size(min = 64, max = 64)
    private String quoteToken;
    public String getQuoteToken() { return quoteToken; }
    public void setQuoteToken(String value) { quoteToken = value; }

    // Getter and Setter
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

}
