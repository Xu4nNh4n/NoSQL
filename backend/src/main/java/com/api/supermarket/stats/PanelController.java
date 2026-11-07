package com.api.supermarket.stats;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.inventory.Batch;
import com.api.supermarket.stats.PosPanelService.Panel;

@RestController
public class PanelController {
    private final PosPanelService panel;
    private final ExpiringSoonService expiring;

    public PanelController(PosPanelService panel, ExpiringSoonService expiring) {
        this.panel = panel;
        this.expiring = expiring;
    }

    @GetMapping("/api/pos/products/{id}/panel")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Panel panel(@PathVariable String id) {
        return panel.panel(id);
    }

    @GetMapping("/api/inventory/batches/expiring-soon")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE')")
    public List<Batch> expiringSoon() {
        return expiring.expiringSoon();
    }
}
