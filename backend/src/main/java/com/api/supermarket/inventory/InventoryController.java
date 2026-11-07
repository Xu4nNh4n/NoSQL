package com.api.supermarket.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.inventory.BatchService.ReceiveCommand;
import com.api.supermarket.inventory.LowStockService.LowStockItem;
import com.api.supermarket.security.JwtPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    public record ReceiveBatchRequest(@NotBlank String productId, String supplierId, @Min(1) int quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 0) BigDecimal unitCost, LocalDate mfgDate,
            LocalDate expiryDate) {
    }

    public record AdjustmentRequest(@Min(1) int quantity, @NotBlank @Size(max = 200) String reason) {
    }

    private final BatchService batches;
    private final LowStockService lowStock;

    public InventoryController(BatchService batches, LowStockService lowStock) {
        this.batches = batches;
        this.lowStock = lowStock;
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public PageResponse<Batch> list(
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "expirySortKey") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return batches.list(productId, page, size, sortBy, sortDir);
    }

    @GetMapping("/batches/expired")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public List<Batch> expired() {
        return batches.expired();
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public Batch get(@PathVariable String id) {
        return batches.load(id);
    }

    /** Nhập lô lẻ cho hàng phát sinh ngoài phiếu nhập. */
    @PostMapping("/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public Batch receive(@Valid @RequestBody ReceiveBatchRequest req, @AuthenticationPrincipal JwtPrincipal actor) {
        return batches.receiveLoose(new ReceiveCommand(req.productId(), req.supplierId(), req.quantity(),
                req.unitCost(), req.mfgDate(), req.expiryDate(), null, null), actor);
    }

    @PostMapping("/batches/{id}/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public Batch adjust(@PathVariable String id, @Valid @RequestBody AdjustmentRequest req,
            @AuthenticationPrincipal JwtPrincipal actor) {
        return batches.adjust(id, req.quantity(), req.reason(), actor);
    }

    @GetMapping("/low-stock")
    public List<LowStockItem> lowStock() {
        return lowStock.lowStock();
    }
}
