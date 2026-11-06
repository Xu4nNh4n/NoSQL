package com.api.supermarket.controller;

import com.api.supermarket.integration.*;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class DataIntegrationController {
    private final DataSyncOperations operations;
    private final DataSyncDispatcher dispatcher;
    private final AstraAnalyticsService analytics;

    public DataIntegrationController(DataSyncOperations operations, DataSyncDispatcher dispatcher,
            AstraAnalyticsService analytics) {
        this.operations = operations;
        this.dispatcher = dispatcher;
        this.analytics = analytics;
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return operations.status(); }

    @PostMapping("/dispatch")
    public Map<String, Object> dispatch() { return dispatcher.dispatch(); }

    @PostMapping("/astra/events/export")
    public Map<String, Object> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "OPERATIONS") OperationalAnalyticsService.Dataset dataset,
            @RequestParam(defaultValue = "UTC") String timeZone) {
        if ("Asia/Ho_Chi_Minh".equals(timeZone)) {
            var previous = analytics.export(date.minusDays(1), dataset);
            var current = analytics.export(date, dataset);
            return Map.of("date", date.toString(), "timeZone", timeZone,
                    "rows", ((Number) previous.get("rows")).longValue() + ((Number) current.get("rows")).longValue(),
                    "partitions", java.util.List.of(previous, current));
        }
        if (!"UTC".equals(timeZone)) throw new com.api.supermarket.exception.BadRequestException("Múi giờ không được hỗ trợ");
        return analytics.export(date, dataset);
    }
}
