package com.api.supermarket.sync;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sync")
@PreAuthorize("hasRole('ADMIN')")
public class SyncController {
    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public SyncService.StatusView status() {
        return service.status();
    }

    @PostMapping("/retry")
    public Map<String, Long> retry() {
        return Map.of("reset", service.retryFailed());
    }
}
