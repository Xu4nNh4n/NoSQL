package com.api.supermarket.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.api.supermarket.dto.request.ReceiveBatchRequest;
import com.api.supermarket.dto.response.InventoryBatchResponse;
import com.api.supermarket.dto.request.StockAdjustmentRequest;
import com.api.supermarket.service.InventoryBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/inventory/batches")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAREHOUSE')")
@Tag(name = "Inventory Batches - Quản lý lô hàng", description = "APIs for managing inventory batches with FEFO expiration strategy - APIs quản lý lô hàng tồn kho theo hạn sử dụng và chiến lược FEFO")
public class InventoryBatchController {
    private final InventoryBatchService service;
    public InventoryBatchController(InventoryBatchService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Receive inventory batch - Nhập lô hàng mới", description = "Receive a new inventory batch with manufacturing date, expiry date, and unit cost - Nhập một lô hàng mới vào kho với ngày sản xuất, hạn sử dụng và giá vốn (hỗ trợ chiến lược FEFO)")
    public InventoryBatchResponse receive(@Valid @RequestBody ReceiveBatchRequest request) {
        return InventoryBatchResponse.from(service.receive(request));
    }

    @GetMapping
    @Operation(summary = "List inventory batches - Danh sách lô hàng", description = "List all inventory batches, or filter by productId - Xem toàn bộ kho hoặc lọc lô theo sản phẩm")
    public List<InventoryBatchResponse> list(@RequestParam(required = false) Long productId) {
        return service.list(productId).stream().map(InventoryBatchResponse::from).toList();
    }

    @PostMapping("/{batchId}/adjustments")
    @Operation(summary = "Adjust batch stock - Điều chỉnh giảm tồn kho lô hàng", description = "Decrease quantity of a specific inventory batch with reason - Giảm số lượng tồn kho của một lô hàng cụ thể kèm lý do điều chỉnh")
    public InventoryBatchResponse adjust(@PathVariable Long batchId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return InventoryBatchResponse.from(service.adjustBatch(batchId, request));
    }
}
