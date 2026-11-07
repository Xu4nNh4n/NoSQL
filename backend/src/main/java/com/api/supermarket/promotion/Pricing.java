package com.api.supermarket.promotion;

import java.math.BigDecimal;
import java.util.List;

/** Dữ liệu vào/ra của {@link PromotionEngine}. Tiền là VND nguyên đồng (scale 0). */
public final class Pricing {
    private Pricing() {
    }

    public record CartItem(String productId, String sku, String name, String categoryId, BigDecimal unitPrice,
            int quantity) {
    }

    /** Sản phẩm quà của BUY_X_GET_Y khác loại; {@code available} là tồn khả dụng hiện tại. */
    public record GiftProduct(String productId, String sku, String name, String categoryId, BigDecimal unitPrice,
            int available) {
    }

    /** Khuyến mãi tại lúc bán, chụp lại và lưu luôn trong dòng đơn (không đổi khi khuyến mãi bị sửa sau này). */
    public record PromotionSnapshot(String promotionId, String name, Promotion.Type type, BigDecimal discount,
            Integer freeUnits) {
    }

    /**
     * @param netAmount tiền khách thực trả cho dòng sau khi chia cả khuyến mãi dòng và khuyến mãi đơn
     */
    public record PricedLine(int lineNo, String productId, String sku, String name, String categoryId, int quantity,
            BigDecimal unitPrice, BigDecimal grossAmount, BigDecimal netAmount, boolean gift,
            PromotionSnapshot promotion) {
    }

    public record PricedCart(List<PricedLine> lines, PromotionSnapshot orderPromotion, BigDecimal grossTotal,
            BigDecimal discountTotal, BigDecimal total, List<String> warnings, String quoteHash) {
    }
}
