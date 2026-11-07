package com.api.supermarket.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Phiếu nhập (collection {@code purchase_orders}): một document chứa nhiều dòng, giống đơn hàng. */
@Data
@NoArgsConstructor
@Document(PurchaseOrder.COLLECTION)
public class PurchaseOrder {
    public static final String COLLECTION = "purchase_orders";

    public enum Status {
        DRAFT, RECEIVED
    }

    @Id
    private String id;
    private String poCode;
    private String supplierId;
    private String supplierName;
    private Status status;
    private String note;
    private List<Line> lines = new ArrayList<>();
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String receivedBy;
    private Instant receivedAt;
    @Version
    private Long version;

    @Data
    @NoArgsConstructor
    public static class Line {
        private int lineNo;
        private String productId;
        private String sku;
        private String productName;
        private int orderedQty;
        private BigDecimal unitCost;
        private int receivedQty;
        private LocalDate mfgDate;
        private LocalDate expiryDate;
        private String batchId;
        private String batchCode;
    }
}
