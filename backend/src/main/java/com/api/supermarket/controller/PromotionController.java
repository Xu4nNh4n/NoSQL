package com.api.supermarket.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.api.supermarket.dto.request.PromotionRequest;
import com.api.supermarket.dto.response.PromotionResponse;
import com.api.supermarket.service.PromotionService;

@RestController
@RequestMapping("/api/promotions")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class PromotionController {
    private final PromotionService promotions;
    public PromotionController(PromotionService promotions) { this.promotions = promotions; }
    @GetMapping
    public List<PromotionResponse> list() { return promotions.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse create(@Valid @RequestBody PromotionRequest request) { return promotions.save(null, request); }
    @PutMapping("/{id}")
    public PromotionResponse update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) { return promotions.save(id, request); }
    public record ActiveRequest(@NotNull Boolean active) {}
    @PutMapping("/{id}/active")
    public PromotionResponse active(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return promotions.setActive(id, request.active());
    }
}
