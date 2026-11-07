package com.api.supermarket.health;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatastoreHealthController {
    public record DatastoresResponse(List<DatastoreHealthService.DatastoreStatus> datastores, Instant checkedAt) {
    }

    private final DatastoreHealthService service;

    public DatastoreHealthController(DatastoreHealthService service) {
        this.service = service;
    }

    @GetMapping("/api/health/datastores")
    @PreAuthorize("hasRole('ADMIN')")
    public DatastoresResponse datastores() {
        return new DatastoresResponse(service.check(), Instant.now());
    }
}
