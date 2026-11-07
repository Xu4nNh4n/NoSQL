package com.api.supermarket.promotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.api.supermarket.promotion.Pricing.CartItem;
import com.api.supermarket.promotion.Pricing.GiftProduct;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.promotion.Pricing.PricedLine;

class PromotionEngineTest {
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private static final Function<String, Optional<GiftProduct>> NO_GIFTS = id -> Optional.empty();

    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    private static CartItem item(String id, String category, long price, int qty) {
        return new CartItem(id, "SKU-" + id, "SP " + id, category, vnd(price), qty);
    }

    private static Promotion promo(String id, Promotion.Type type, Instant startsAt) {
        Promotion p = new Promotion();
        p.setId(id);
        p.setName("KM " + id);
        p.setType(type);
        p.setActive(true);
        p.setStartsAt(startsAt);
        p.setEndsAt(NOW.plusSeconds(86_400));
        return p;
    }

    private static Promotion percent(String id, String productId, int pct) {
        Promotion p = promo(id, Promotion.Type.PERCENT, NOW.minusSeconds(3600));
        p.getScope().getProductIds().add(productId);
        p.getRule().setPercent(BigDecimal.valueOf(pct));
        return p;
    }

    private static Promotion fixed(String id, String productId, long amount) {
        Promotion p = promo(id, Promotion.Type.FIXED, NOW.minusSeconds(3600));
        p.getScope().getProductIds().add(productId);
        p.getRule().setAmount(vnd(amount));
        return p;
    }

    private static Promotion buyGet(String id, String productId, int buy, int get, String giftId) {
        Promotion p = promo(id, Promotion.Type.BUY_X_GET_Y, NOW.minusSeconds(3600));
        p.getScope().getProductIds().add(productId);
        p.getRule().setBuyQty(buy);
        p.getRule().setGetQty(get);
        p.getRule().setGiftProductId(giftId);
        return p;
    }

    private static Promotion threshold(String id, long minTotal, Long amount, Integer pct, Long max) {
        Promotion p = promo(id, Promotion.Type.ORDER_THRESHOLD, NOW.minusSeconds(3600));
        p.getRule().setMinTotal(vnd(minTotal));
        p.getRule().setAmount(amount == null ? null : vnd(amount));
        p.getRule().setPercent(pct == null ? null : BigDecimal.valueOf(pct));
        p.getRule().setMaxDiscount(max == null ? null : vnd(max));
        return p;
    }

    private static void assertTotalsConsistent(PricedCart cart) {
        BigDecimal sumNet = cart.lines().stream().map(PricedLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(cart.total(), sumNet, "tổng netAmount phải bằng total");
        assertEquals(cart.grossTotal().subtract(cart.discountTotal()), cart.total());
        cart.lines().forEach(l -> assertTrue(l.netAmount().signum() >= 0, "không có dòng âm"));
    }

    @Test
    void srsExampleBuy2Get1Milk() {
        PricedCart cart = PromotionEngine.price(List.of(item("milk", "c1", 30_000, 3)),
                List.of(buyGet("b21", "milk", 2, 1, null)), NO_GIFTS, NOW);
        PricedLine line = cart.lines().get(0);
        assertEquals(vnd(90_000), line.grossAmount());
        assertEquals(vnd(60_000), line.netAmount());
        assertEquals(1, line.promotion().freeUnits());
        assertEquals(vnd(60_000), cart.total());
    }

    @Test
    void buyXGetYSameProductCountsCompleteGroupsOnly() {
        Promotion p = buyGet("b21", "milk", 2, 1, null);
        assertEquals(vnd(20_000 * 4), PromotionEngine.price(List.of(item("milk", "c1", 20_000, 5)), List.of(p), NO_GIFTS, NOW)
                .total()); // 5 cái: 1 nhóm (3) + 2 lẻ → tặng 1
        assertEquals(vnd(20_000 * 4), PromotionEngine.price(List.of(item("milk", "c1", 20_000, 6)), List.of(p), NO_GIFTS, NOW)
                .total()); // 6 cái: 2 nhóm → tặng 2
    }

    @Test
    void picksTheBestLinePromotion() {
        PricedCart cart = PromotionEngine.price(List.of(item("a", "c1", 30_000, 2)),
                List.of(percent("p10", "a", 10), fixed("f5k", "a", 5_000)), NO_GIFTS, NOW);
        // 10% của 60k = 6k; giảm 5k/cái × 2 = 10k → chọn FIXED
        assertEquals("f5k", cart.lines().get(0).promotion().promotionId());
        assertEquals(vnd(50_000), cart.total());
    }

    @Test
    void tieGoesToNewerPromotion() {
        Promotion older = percent("old", "a", 10);
        older.setStartsAt(NOW.minusSeconds(7200));
        Promotion newer = fixed("new", "a", 1_000);
        newer.setStartsAt(NOW.minusSeconds(60));
        PricedCart cart = PromotionEngine.price(List.of(item("a", "c1", 10_000, 1)), List.of(older, newer), NO_GIFTS, NOW);
        assertEquals("new", cart.lines().get(0).promotion().promotionId());
    }

    @Test
    void fixedDiscountNeverGoesBelowZero() {
        PricedCart cart = PromotionEngine.price(List.of(item("a", "c1", 3_000, 2)), List.of(fixed("f", "a", 5_000)),
                NO_GIFTS, NOW);
        assertEquals(BigDecimal.ZERO, cart.total().stripTrailingZeros().abs());
        assertTotalsConsistent(cart);
    }

    @Test
    void categoryScopeAndTimeWindow() {
        Promotion byCategory = promo("cat", Promotion.Type.PERCENT, NOW.minusSeconds(10));
        byCategory.getScope().getCategoryIds().add("dairy");
        byCategory.getRule().setPercent(BigDecimal.valueOf(20));
        Promotion expired = percent("exp", "b", 50);
        expired.setEndsAt(NOW.minusSeconds(1));
        Promotion inactive = percent("off", "b", 50);
        inactive.setActive(false);

        PricedCart cart = PromotionEngine.price(List.of(item("a", "dairy", 10_000, 1), item("b", "snack", 10_000, 1)),
                List.of(byCategory, expired, inactive), NO_GIFTS, NOW);
        assertEquals("cat", cart.lines().get(0).promotion().promotionId());
        assertNull(cart.lines().get(1).promotion());
        assertEquals(vnd(18_000), cart.total());
    }

    @Test
    void sameProductScannedTwiceIsMerged() {
        PricedCart cart = PromotionEngine.price(List.of(item("milk", "c1", 30_000, 2), item("milk", "c1", 30_000, 1)),
                List.of(buyGet("b21", "milk", 2, 1, null)), NO_GIFTS, NOW);
        assertEquals(1, cart.lines().size());
        assertEquals(3, cart.lines().get(0).quantity());
        assertEquals(vnd(60_000), cart.total());
    }

    @Test
    void giftOfOtherProductAddsGiftLineAndSplitsPackagePrice() {
        GiftProduct glass = new GiftProduct("glass", "LY", "Ly thủy tinh", "c9", vnd(10_000), 100);
        PricedCart cart = PromotionEngine.price(List.of(item("beer", "c2", 20_000, 2)),
                List.of(buyGet("beerglass", "beer", 2, 1, "glass")), id -> Optional.of(glass), NOW);
        assertEquals(2, cart.lines().size());
        PricedLine beer = cart.lines().get(0);
        PricedLine gift = cart.lines().get(1);
        assertTrue(gift.gift());
        assertEquals(1, gift.quantity());
        // khách trả 40k cho gói niêm yết 50k: bia 40k × 40/50 = 32k, ly 8k
        assertEquals(vnd(32_000), beer.netAmount());
        assertEquals(vnd(8_000), gift.netAmount());
        assertEquals(vnd(40_000), cart.total());
        assertEquals(vnd(50_000), cart.grossTotal());
        assertTotalsConsistent(cart);
    }

    @Test
    void giftOutOfStockFallsBackWithWarning() {
        GiftProduct glass = new GiftProduct("glass", "LY", "Ly thủy tinh", "c9", vnd(10_000), 0);
        PricedCart cart = PromotionEngine.price(List.of(item("beer", "c2", 20_000, 2)),
                List.of(buyGet("beerglass", "beer", 2, 1, "glass"), percent("p5", "beer", 5)), id -> Optional.of(glass), NOW);
        assertEquals(1, cart.lines().size());
        assertEquals("p5", cart.lines().get(0).promotion().promotionId());
        assertFalse(cart.warnings().isEmpty());
    }

    @Test
    void orderThresholdPicksBestAndAppliesAfterLinePromotions() {
        PricedCart cart = PromotionEngine.price(List.of(item("a", "c1", 300_000, 1), item("b", "c1", 250_000, 1)),
                List.of(percent("p10", "a", 10), threshold("t50", 500_000, 50_000L, null, null),
                        threshold("t10pct", 500_000, null, 10, 40_000L)),
                NO_GIFTS, NOW);
        // sau KM dòng: 270k + 250k = 520k ≥ 500k; giảm 50k > min(52k, trần 40k) → chọn t50
        assertEquals("t50", cart.orderPromotion().promotionId());
        assertEquals(vnd(470_000), cart.total());
        assertTotalsConsistent(cart);
    }

    @Test
    void thresholdNotReachedAfterLineDiscounts() {
        PricedCart cart = PromotionEngine.price(List.of(item("a", "c1", 500_000, 1)),
                List.of(percent("p10", "a", 10), threshold("t", 500_000, 50_000L, null, null)), NO_GIFTS, NOW);
        assertNull(cart.orderPromotion());
        assertEquals(vnd(450_000), cart.total());
    }

    @Test
    void orderDiscountRemainderGoesToLastLine() {
        // 100k chia 3 dòng 1/1/1 = 33.333 mỗi dòng → 33.333, 33.333, 33.334
        PricedCart cart = PromotionEngine.price(
                List.of(item("a", "c1", 100_000, 1), item("b", "c1", 100_000, 1), item("c", "c1", 100_000, 1)),
                List.of(threshold("t", 300_000, 100_000L, null, null)), NO_GIFTS, NOW);
        assertEquals(Map.of(1, vnd(66_667), 2, vnd(66_667), 3, vnd(66_666)), Map.of(
                1, cart.lines().get(0).netAmount(), 2, cart.lines().get(1).netAmount(), 3, cart.lines().get(2).netAmount()));
        assertEquals(vnd(200_000), cart.total());
        assertTotalsConsistent(cart);
    }

    @Test
    void quoteHashChangesWhenPriceChanges() {
        PricedCart a = PromotionEngine.price(List.of(item("a", "c1", 10_000, 1)), List.of(), NO_GIFTS, NOW);
        PricedCart b = PromotionEngine.price(List.of(item("a", "c1", 11_000, 1)), List.of(), NO_GIFTS, NOW);
        PricedCart a2 = PromotionEngine.price(List.of(item("a", "c1", 10_000, 1)), List.of(), NO_GIFTS, NOW);
        assertNotEquals(a.quoteHash(), b.quoteHash());
        assertEquals(a.quoteHash(), a2.quoteHash());
    }
}
