package com.api.supermarket.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lô hàng (collection {@code batches}). {@code expirySortKey} = hạn dùng, hoặc 9999-12-31 khi không có hạn,
 * để sắp FEFO đúng (MongoDB xếp null lên đầu). Tên/mã sản phẩm và tên NCC được chép để danh sách và đồ thị
 * không phải tra thêm.
 */
@Data
@NoArgsConstructor
@Document(Batch.COLLECTION)
public class Batch {
    public static final String COLLECTION = "batches";
    public static final LocalDate NO_EXPIRY = LocalDate.of(9999, 12, 31);

    @Id
    private String id;
    private String batchCode;
    private String productId;
    private String productSku;
    private String productName;
    private String supplierId;
    private String supplierName;
    private String purchaseOrderId;
    private String purchaseOrderCode;
    private int receivedQty;
    private int remainingQty;
    private int damagedQty;
    private BigDecimal unitCost;
    private LocalDate mfgDate;
    private LocalDate expiryDate;
    private LocalDate expirySortKey;
    private Instant receivedAt;
    private String receivedBy;
    private List<Adjustment> adjustments = new ArrayList<>();

    /** Lịch sử điều chỉnh giảm ngay trong document lô (thẻ kho trên Cassandra cũng có dòng ADJUSTMENT). */
    public record Adjustment(String id, int quantity, String reason, String by, Instant at) {
    }
}
