package com.api.supermarket.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.StockMovementResponse;
import com.api.supermarket.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/products/{productId}/stock-movements")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'MANAGER', 'WAREHOUSE')")
@Tag(name = "Stock Movements - Lịch sử biến động tồn kho", description = "Track inventory movements (sale, reservation, release, adjustment, return) - Theo dõi nhật ký xuất nhập tồn của sản phẩm")
public class StockMovementController {
    private final ProductService productService;

    public StockMovementController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Get stock movements - Xem lịch sử biến động tồn kho", description = "Get paginated history of stock movements for a specific product - Lấy danh sách lịch sử biến động kho có phân trang của sản phẩm")
    public PageResponse<StockMovementResponse> getMovements(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.getStockMovements(productId, page, size);
    }
}
