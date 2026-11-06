package com.api.supermarket.service;

import java.util.List;

import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;

public interface SalesOrderService {
    com.api.supermarket.dto.response.PosQuoteResponse quote(com.api.supermarket.dto.request.PosQuoteRequest request);

    // Lấy 1 hóa đơn cụ thể
    SalesOrderResponse getSalesOrderById(Long orderId);

    PageResponse<SalesOrderResponse> getAllSalesOrders();

    // Tạo hóa đơn mới
    SalesOrderResponse createSalesOrder(SalesOrderRequest request);


    com.api.supermarket.dto.response.SalesOrderReturnResponse returnSalesOrder(Long orderId,
            com.api.supermarket.dto.request.ReturnOrderRequest request);

    com.api.supermarket.dto.response.SalesOrderReturnResponse confirmReturnRefund(Long returnId,
            com.api.supermarket.dto.request.PaymentConfirmationRequest request);

    List<com.api.supermarket.dto.response.SalesOrderReturnResponse> getReturns(Long orderId);

    List<com.api.supermarket.dto.response.PaymentRecordResponse> getPaymentRecords(Long orderId);

    // Tìm Kiếm theo mã in trên hóa đơn
    SalesOrderResponse getSalesOrderCode(String orderCode);

    // Lọc hóa đơn theo trạng thái
    PageResponse<SalesOrderResponse> filterSalesOrders(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String status,
            String paymentMethod,
            String searchTerm);
}
