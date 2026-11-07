package com.api.supermarket.promotion;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.api.supermarket.promotion.PromotionService.PromotionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/promotions")
@PreAuthorize("hasRole('ADMIN')")
public class PromotionController {
    public record ActiveRequest(@NotNull Boolean active) {
    }

    private final PromotionService service;

    public PromotionController(PromotionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Promotion> list(
            @RequestParam(required = false) Promotion.Type type,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startsAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return service.list(type, active, page, size, sortBy, sortDir);
    }

    /** Khuyến mãi đang chạy, cho màn hình bán hàng (đọc cache Redis). */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public List<Promotion> active() {
        return service.active();
    }

    @GetMapping("/{id}")
    public Promotion get(@PathVariable String id) {
        return service.load(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Promotion create(@Valid @RequestBody PromotionRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Promotion update(@PathVariable String id, @Valid @RequestBody PromotionRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/{id}/active")
    public Promotion setActive(@PathVariable String id, @Valid @RequestBody ActiveRequest req) {
        return service.setActive(id, req.active());
    }
}
