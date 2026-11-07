package com.api.supermarket.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.inventory.PurchaseOrderService.PurchaseOrderRequest;
import com.api.supermarket.inventory.PurchaseOrderService.ReceiveRequest;
import com.api.supermarket.security.JwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
public class PurchaseOrderController {
    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PurchaseOrder> list(
            @RequestParam(required = false) PurchaseOrder.Status status,
            @RequestParam(required = false) String supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return service.list(status, supplierId, page, size, sortBy, sortDir);
    }

    @GetMapping("/{id}")
    public PurchaseOrder get(@PathVariable String id) {
        return service.load(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder create(@Valid @RequestBody PurchaseOrderRequest req, @AuthenticationPrincipal JwtPrincipal actor) {
        return service.create(req, actor);
    }

    @PutMapping("/{id}")
    public PurchaseOrder update(@PathVariable String id, @Valid @RequestBody PurchaseOrderRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrder receive(@PathVariable String id, @Valid @RequestBody ReceiveRequest req,
            @AuthenticationPrincipal JwtPrincipal actor) {
        return service.receive(id, req, actor);
    }
}
