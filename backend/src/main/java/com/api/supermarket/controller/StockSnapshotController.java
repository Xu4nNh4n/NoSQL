package com.api.supermarket.controller;

import com.api.supermarket.integration.StockSnapshotService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class StockSnapshotController {
    private final StockSnapshotService snapshots;

    public StockSnapshotController(StockSnapshotService snapshots) { this.snapshots = snapshots; }

    @PostMapping("/api/integrations/cassandra/stock-snapshots")
    public ResponseEntity<Map<String, Object>> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<String, Object> result = snapshots.generate(from, to);
        return ResponseEntity.status("REJECTED".equals(result.get("status"))
                ? HttpStatus.PRECONDITION_FAILED : HttpStatus.OK).body(result);
    }
}
