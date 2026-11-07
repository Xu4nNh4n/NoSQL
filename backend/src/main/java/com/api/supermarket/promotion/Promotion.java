package com.api.supermarket.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Khuyến mãi (collection {@code promotions}). Bốn loại cùng nằm một collection, phân biệt bằng {@code type};
 * {@code rule} chỉ có các trường của loại đó vì trường null không được ghi xuống document. Đây là ví dụ rõ nhất
 * cho schema linh hoạt của MongoDB:
 * <pre>
 * PERCENT          { percent: 20 }
 * FIXED            { amount: 2000 }
 * BUY_X_GET_Y      { buyQty: 2, getQty: 1, giftProductId?: "..." }
 * ORDER_THRESHOLD  { minTotal: 500000, amount: 50000 } | { minTotal, percent, maxDiscount? }
 * </pre>
 */
@Data
@NoArgsConstructor
@Document(Promotion.COLLECTION)
public class Promotion {
    public static final String COLLECTION = "promotions";

    public enum Type {
        PERCENT, FIXED, BUY_X_GET_Y, ORDER_THRESHOLD;

        public boolean isLineLevel() {
            return this != ORDER_THRESHOLD;
        }
    }

    @Id
    private String id;
    private String name;
    private Type type;
    private boolean active;
    private Instant startsAt;
    private Instant endsAt;
    private Scope scope = new Scope();
    private Rule rule = new Rule();
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private Long version;

    @Data
    @NoArgsConstructor
    public static class Scope {
        private List<String> productIds = new ArrayList<>();
        private List<String> categoryIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Rule {
        private BigDecimal percent;
        private BigDecimal amount;
        private Integer buyQty;
        private Integer getQty;
        private String giftProductId;
        private BigDecimal minTotal;
        private BigDecimal maxDiscount;
    }

    /** Đang chạy tại thời điểm {@code at}: bật, đã bắt đầu, chưa kết thúc. */
    public boolean runningAt(Instant at) {
        return active && startsAt != null && endsAt != null && !at.isBefore(startsAt) && at.isBefore(endsAt);
    }

    /** Khuyến mãi dòng có áp lên sản phẩm này không (theo mã sản phẩm hoặc nhóm hàng). */
    public boolean appliesTo(String productId, String categoryId) {
        if (scope == null) {
            return false;
        }
        if (scope.getProductIds() != null && scope.getProductIds().contains(productId)) {
            return true;
        }
        return type != Type.BUY_X_GET_Y && categoryId != null && scope.getCategoryIds() != null
                && scope.getCategoryIds().contains(categoryId);
    }
}
