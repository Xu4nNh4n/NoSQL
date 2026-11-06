package com.api.supermarket.controller;

import com.api.supermarket.integration.DrillAnalyticsService;
import com.api.supermarket.integration.OperationalAnalyticsService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AnalyticsController {
    private final DrillAnalyticsService reports;
    private final OperationalAnalyticsService operations;

    public AnalyticsController(DrillAnalyticsService reports, OperationalAnalyticsService operations) { this.reports = reports; this.operations = operations; }

    @GetMapping("/operational")
    public Map<String, Object> operational(
            @RequestParam OperationalAnalyticsService.Report report,
            @RequestParam(defaultValue = "OPERATIONS") OperationalAnalyticsService.Dataset dataset,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") int top,
            @RequestParam(defaultValue = "7") int horizon,
            @RequestParam(defaultValue = "7") int leadDays,
            @RequestParam(defaultValue = "14") int coverageDays) {
        return operations.report(report, dataset, from, to, top, horizon, leadDays, coverageDays);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dashboard(from, to);
    }

    @GetMapping("/inventory")
    public Map<String, Object> inventory(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return reports.inventory(page, size);
    }

    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "OPERATIONS") OperationalAnalyticsService.Dataset dataset) {
        return reports.cashFlow(from, to, dataset);
    }

    @GetMapping("/reconciliation")
    public Map<String, Object> reconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "OPERATIONS") OperationalAnalyticsService.Dataset dataset) {
        return reports.reconciliation(from, to, dataset);
    }

}
