package com.api.supermarket.graph;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.graph.GraphQueryService.TracedOrder;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.inventory.BatchService;
import com.api.supermarket.security.JwtPrincipal;

/**
 * Truy vết và thu hồi lô (FR-13): thông tin lô lấy từ MongoDB (nguồn sự thật về tồn), đường đi lô → đơn → khách
 * lấy từ Neo4j. Thu hồi = điều chỉnh giảm toàn bộ phần còn tồn và trả danh sách khách cần liên hệ.
 */
@Service
public class RecallService {
    public record Contact(String customerId, String name, String phone, long quantity) {
    }

    public record TraceResult(String batchCode, String productId, String productSku, String productName,
            String supplierName, LocalDate expiryDate, int receivedQty, int remainingQty, long soldQty,
            List<TracedOrder> orders, List<Contact> contacts) {
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
        Map<String, Contact> contacts = new LinkedHashMap<>();
        for (TracedOrder o : orders) {
            if (o.customerId() == null) {
                continue; // khách lẻ không có hồ sơ chỉ hiện mã đơn
            }
            contacts.merge(o.customerId(), new Contact(o.customerId(), o.customerName(), o.customerPhone(), o.quantity()),
                    (a, c) -> new Contact(a.customerId(), a.name(), a.phone(), a.quantity() + c.quantity()));
        }
        long sold = orders.stream().mapToLong(TracedOrder::quantity).sum();
        return new TraceResult(b.getBatchCode(), b.getProductId(), b.getProductSku(), b.getProductName(),
                b.getSupplierName(), b.getExpiryDate(), b.getReceivedQty(), b.getRemainingQty(), sold, orders,
                List.copyOf(contacts.values()));
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
