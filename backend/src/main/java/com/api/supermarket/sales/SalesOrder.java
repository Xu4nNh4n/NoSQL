package com.api.supermarket.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api.supermarket.promotion.Pricing.PromotionSnapshot;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đơn bán (collection {@code orders}, spec 5.2): một document chứa dòng hàng, lô đã xuất cho từng dòng và
 * các khoản thanh toán/hoàn. Đọc/ghi cả đơn trong một lần, kiểm tra "hoàn không vượt số đã thu" chỉ cần
 * đọc một document.
 */
@Data
@NoArgsConstructor
@Document(SalesOrder.COLLECTION)
public class SalesOrder {
    public static final String COLLECTION = "orders";

    public enum Status {
        PAID, PARTIALLY_RETURNED, RETURNED
    }

    public enum Method {
        CASH, BANK_TRANSFER
    }

    @Id
    private String id;
    private String orderCode;
    private String requestKey;
    /** SHA-256 nội dung yêu cầu, để phân biệt gửi lặp (giống) với dùng trùng requestKey (khác). */
    private String requestFingerprint;
    private PersonRef cashier;
    private Instant orderedAt;
    private LocalDate orderDay;
    private Status status;
    private Method paymentMethod;
    private String transactionRef;
    private BigDecimal grossTotal;
    private BigDecimal discountTotal;
    private BigDecimal total;
    private BigDecimal refundedTotal = BigDecimal.ZERO;
    private PromotionSnapshot orderPromotion;
    private List<OrderLine> items = new ArrayList<>();
    private List<Payment> payments = new ArrayList<>();
    @Version
    private Long version;

    public record PersonRef(String id, String name, String phone) {
    }

    @Data
    @NoArgsConstructor
    public static class OrderLine {
        private int lineNo;
        private String productId;
        private String sku;
        private String name;
        private String categoryId;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal grossAmount;
        /** Tiền khách thực trả cho dòng, sau khi chia khuyến mãi dòng và khuyến mãi đơn. */
        private BigDecimal netAmount;
        private boolean gift;
        private PromotionSnapshot promotion;
        private int returnedQuantity;
        private List<Allocation> allocations = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Allocation {
        private String batchId;
        private String batchCode;
        private LocalDate expiryDate;
        private int quantity;
        private int returnedQuantity;
    }

    @Data
    @NoArgsConstructor
    public static class Payment {
        public enum Type {
            PAYMENT, REFUND
        }

        private String paymentId;
        private Type type;
        private Method method;
        private BigDecimal amount;
        private String reference;
        private String returnId;
        private String returnCode;
        private Instant at;
        private String by;
    }
}
