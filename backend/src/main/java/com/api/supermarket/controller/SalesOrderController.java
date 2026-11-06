package com.api.supermarket.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.PaymentConfirmationRequest;
import com.api.supermarket.dto.request.ReturnOrderRequest;
import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.PaymentRecordResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.service.SalesOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Sales Orders - Hóa đơn POS", description = "Create and manage in-store point-of-sale orders, returns and refunds - Tạo và quản lý hóa đơn bán tại quầy, trả hàng và hoàn tiền")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private static final Logger log = LoggerFactory.getLogger(SalesOrderController.class);

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "Create POS sale - Tạo hóa đơn bán hàng tại quầy", description = "Create a paid in-store sale after verifying stock - Tạo đơn bán tại quầy sau khi kiểm tra tồn kho")
    public ResponseEntity<SalesOrderResponse> createSalesOrder(@Valid @RequestBody SalesOrderRequest request) {
        log.info("Nhận yêu cầu tạo hóa đơn mới từ thu ngân");
        SalesOrderResponse response = salesOrderService.createSalesOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/quote")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<com.api.supermarket.dto.response.PosQuoteResponse> quote(
            @Valid @RequestBody com.api.supermarket.dto.request.PosQuoteRequest request) {
        return ResponseEntity.ok(salesOrderService.quote(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get order by ID - Xem chi tiết hóa đơn theo ID", description = "Retrieve complete sales order information by order ID - Lấy thông tin chi tiết của một hóa đơn theo mã ID")
    public ResponseEntity<SalesOrderResponse> getSalesOrderById(@PathVariable Long id) {
        log.debug("Xem chi tiết hóa đơn, orderId = {}", id);
        return ResponseEntity.ok(salesOrderService.getSalesOrderById(id));
    }

    @GetMapping("/code/{orderCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get order by code - Tra cứu hóa đơn theo mã in", description = "Find sales order by printed receipt code - Tra cứu hóa đơn bán hàng bằng mã in trên phiếu thu")
    public ResponseEntity<SalesOrderResponse> getSalesOrderByCode(@PathVariable String orderCode) {
        log.debug("Tra cứu hóa đơn theo mã: {}", orderCode);
        return ResponseEntity.ok(salesOrderService.getSalesOrderCode(orderCode));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Filter sales orders - Lọc và phân trang danh sách hóa đơn", description = "Paginate and filter orders by status, payment method, or search term - Phân trang và tìm kiếm đơn hàng theo trạng thái, hình thức thanh toán")
    public ResponseEntity<PageResponse<SalesOrderResponse>> filterSalesOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String searchTerm) {
        PageResponse<SalesOrderResponse> response = salesOrderService.filterSalesOrders(page, size, sortBy, sortDir,
                status, paymentMethod, searchTerm);
        return ResponseEntity.ok(response);
    }


    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Nhận trả từng món của hóa đơn POS", description = "Chọn món và số lượng thực nhận, không vượt lượng còn được trả; xác định lô gốc khi trả một phần từ nhiều lô. Hàng nhận được ghi vào phiếu, tiền hoàn xác nhận riêng theo phiếu trả")
    public ResponseEntity<com.api.supermarket.dto.response.SalesOrderReturnResponse> returnSalesOrder(@PathVariable Long id,
            @Valid @RequestBody ReturnOrderRequest request) {
        return ResponseEntity.ok(salesOrderService.returnSalesOrder(id, request));
    }

    @PutMapping("/returns/{returnId}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Confirm return refund - Xác nhận hoàn tiền cho phiếu trả hàng", description = "Confirm refund transaction for a specific return receipt - Xác nhận hoàn tiền theo phiếu trả hàng cụ thể")
    public ResponseEntity<com.api.supermarket.dto.response.SalesOrderReturnResponse> confirmReturnRefund(
            @PathVariable Long returnId, @Valid @RequestBody PaymentConfirmationRequest request) {
        return ResponseEntity.ok(salesOrderService.confirmReturnRefund(returnId, request));
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Get order return receipts - Danh sách phiếu trả hàng của hóa đơn", description = "Retrieve list of all return receipts for an order - Lấy danh sách tất cả các phiếu trả hàng của một đơn hàng")
    public ResponseEntity<List<com.api.supermarket.dto.response.SalesOrderReturnResponse>> getReturns(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.getReturns(id));
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Get payment records - Xem lịch sử thanh toán hóa đơn", description = "Retrieve list of all payment and refund transactions of an order - Lấy lịch sử chi tiết các giao dịch thanh toán và hoàn tiền của đơn")
    public ResponseEntity<List<PaymentRecordResponse>> getPaymentRecords(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.getPaymentRecords(id));
    }
}
