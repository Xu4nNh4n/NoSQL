package com.api.supermarket.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Phiếu trả kèm hoàn tiền (collection {@code returns}). Một phiếu đúng một khoản hoàn. */
@Data
@NoArgsConstructor
@Document(SalesReturn.COLLECTION)
public class SalesReturn {
    public static final String COLLECTION = "returns";

    @Id
    private String id;
    private String returnCode;
    private String orderId;
    private String orderCode;
    private String requestKey;
    private String requestFingerprint;
    private List<Item> items = new ArrayList<>();
    private BigDecimal refundAmount;
    private SalesOrder.Method refundMethod;
    private String refundReference;
    private SalesOrder.PersonRef createdBy;
    private Instant createdAt;

    @Data
    @NoArgsConstructor
    public static class Item {
        private int lineNo;
        private String productId;
        private String sku;
        private String name;
        private int quantity;
        private boolean restock;
        private String reason;
        private BigDecimal refundAmount;
        private List<BatchPart> batches = new ArrayList<>();
    }

    public record BatchPart(String batchId, String batchCode, int quantity) {
    }
}
