package com.api.supermarket.graph;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.graph.GraphQueryService.TracedOrder;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.inventory.BatchService;
import com.api.supermarket.security.JwtPrincipal;

/**
 * Truy vết và thu hồi lô (FR-12): thông tin lô lấy từ MongoDB (nguồn sự thật về tồn), đường đi lô → đơn
 * lấy từ Neo4j. Thu hồi = điều chỉnh giảm toàn bộ phần còn tồn và trả danh sách đơn đã bán từ lô.
 */
@Service
public class RecallService {
    public record TraceResult(String batchCode, String productId, String productSku, String productName,
            String supplierName, LocalDate expiryDate, int receivedQty, int remainingQty, long soldQty,
            List<TracedOrder> orders) {
    }

    private final BatchRepository batches;
    private final BatchService batchService;
    private final GraphQueryService graph;

    public RecallService(BatchRepository batches, BatchService batchService, GraphQueryService graph) {
        this.batches = batches;
        this.batchService = batchService;
        this.graph = graph;
    }

    public TraceResult trace(String batchCode) {
        Batch b = batches.findByCode(batchCode.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô " + batchCode));
        List<TracedOrder> orders = graph.trace(b.getBatchCode());
        long sold = orders.stream().mapToLong(TracedOrder::quantity).sum();
        return new TraceResult(b.getBatchCode(), b.getProductId(), b.getProductSku(), b.getProductName(),
                b.getSupplierName(), b.getExpiryDate(), b.getReceivedQty(), b.getRemainingQty(), sold, orders);
    }

    public TraceResult recall(String batchCode, String reason, JwtPrincipal admin) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Phải nhập lý do thu hồi");
        }
        Batch b = batches.findByCode(batchCode.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô " + batchCode));
        if (b.getRemainingQty() > 0) {
            batchService.adjust(b.getId(), b.getRemainingQty(), "Thu hồi: " + reason.trim(), admin);
        }
        return trace(b.getBatchCode());
    }
}
