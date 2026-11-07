package com.api.supermarket.graph;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.graph.GraphQueryService.Suggestions;
import com.api.supermarket.graph.GraphRebuildService.RebuildResult;
import com.api.supermarket.graph.RecallService.TraceResult;
import com.api.supermarket.security.JwtPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
public class GraphController {
    public record RecallRequest(@NotBlank @Size(max = 200) String reason) {
    }

    private final RecallService recall;
    private final GraphQueryService graph;
    private final GraphRebuildService rebuild;

    public GraphController(RecallService recall, GraphQueryService graph, GraphRebuildService rebuild) {
        this.recall = recall;
        this.graph = graph;
        this.rebuild = rebuild;
    }

    @GetMapping("/api/batches/{code}/trace")
    @PreAuthorize("hasRole('ADMIN')")
    public TraceResult trace(@PathVariable String code) {
        return recall.trace(code);
    }

    @PostMapping("/api/batches/{code}/recall")
    @PreAuthorize("hasRole('ADMIN')")
    public TraceResult recall(@PathVariable String code, @Valid @RequestBody RecallRequest req,
            @AuthenticationPrincipal JwtPrincipal admin) {
        return recall.recall(code, req.reason(), admin);
    }

    @GetMapping("/api/products/{id}/also-bought")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public Suggestions alsoBought(@PathVariable String id, @RequestParam(defaultValue = "5") int limit) {
        return graph.alsoBought(id, Math.max(1, Math.min(limit, 10)));
    }

    @PostMapping("/api/admin/graph/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public RebuildResult rebuild() {
        return rebuild.rebuild();
    }
}
