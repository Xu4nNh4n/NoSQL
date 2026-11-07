package com.api.supermarket.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.api.supermarket.promotion.Pricing.CartItem;
import com.api.supermarket.promotion.Pricing.GiftProduct;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.promotion.Pricing.PricedLine;
import com.api.supermarket.promotion.Pricing.PromotionSnapshot;

/**
 * Tính giá giỏ hàng với khuyến mãi (spec 10.2). Java thuần, không gọi CSDL, nên test được hết các trường hợp.
 * <ol>
 * <li>Gộp giỏ theo sản phẩm.</li>
 * <li>Mỗi dòng chọn một khuyến mãi dòng có lợi nhất (hòa thì mới hơn).</li>
 * <li>Chọn một khuyến mãi theo tổng đơn có lợi nhất đạt mức tối thiểu.</li>
 * <li>Chia giảm giá đơn theo tỉ lệ tiền từng dòng, làm tròn xuống, phần dư vào dòng cuối.</li>
 * </ol>
 */
public final class PromotionEngine {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Comparator<Promotion> NEWER_FIRST =
            Comparator.comparing(Promotion::getStartsAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed();

    private PromotionEngine() {
    }

    /** Kết quả tạm của một dòng khi xét một khuyến mãi dòng. */
    private record Candidate(Promotion promotion, BigDecimal benefit, int freeUnits, GiftProduct gift, int giftQty) {
    }

    /** Dòng đang tính: tiền sau khuyến mãi dòng ({@code net}) sẽ còn bị trừ phần chia của khuyến mãi đơn. */
    private static final class Work {
        String productId, sku, name, categoryId;
        int quantity;
        BigDecimal unitPrice, gross, net;
        boolean gift;
        Promotion promotion;
        int freeUnits;
    }

    public static PricedCart price(List<CartItem> cart, List<Promotion> promotions,
            Function<String, Optional<GiftProduct>> gifts, Instant now) {
        List<Promotion> running = promotions.stream().filter(p -> p.runningAt(now)).sorted(NEWER_FIRST).toList();
        List<Promotion> linePromos = running.stream().filter(p -> p.getType().isLineLevel()).toList();
        List<Promotion> orderPromos = running.stream().filter(p -> !p.getType().isLineLevel()).toList();

        Map<String, CartItem> merged = merge(cart);
        Map<String, Integer> cartQty = new LinkedHashMap<>();
        merged.values().forEach(i -> cartQty.put(i.productId(), i.quantity()));
        Map<String, Integer> giftsReserved = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        List<Work> lines = new ArrayList<>();

        for (CartItem item : merged.values()) {
            BigDecimal gross = money(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
            Candidate best = null;
            for (Promotion p : linePromos) {
                if (!p.appliesTo(item.productId(), item.categoryId())) {
                    continue;
                }
                Candidate c = evaluate(p, item, gross, gifts, cartQty, giftsReserved, warnings);
                if (c != null && c.benefit().signum() > 0 && (best == null || c.benefit().compareTo(best.benefit()) > 0)) {
                    best = c; // danh sách đã xếp mới trước, nên khi hòa giữ cái mới hơn
                }
            }
            Work w = work(item.productId(), item.sku(), item.name(), item.categoryId(), item.quantity(), item.unitPrice());
            lines.add(w);
            if (best == null) {
                continue;
            }
            w.promotion = best.promotion();
            if (best.gift() == null) {
                w.net = gross.subtract(best.benefit());
                w.freeUnits = best.freeUnits();
            } else {
                GiftProduct g = best.gift();
                giftsReserved.merge(g.productId(), best.giftQty(), Integer::sum);
                Work giftLine = work(g.productId(), g.sku(), g.name(), g.categoryId(), best.giftQty(), g.unitPrice());
                giftLine.gift = true;
                giftLine.promotion = best.promotion();
                giftLine.freeUnits = best.giftQty();
                // Khách trả đúng tiền hàng mua; số tiền đó chia cho dòng mua và dòng quà theo tỉ lệ giá niêm yết.
                BigDecimal paid = gross;
                BigDecimal listed = gross.add(giftLine.gross);
                w.net = listed.signum() == 0 ? BigDecimal.ZERO
                        : paid.multiply(gross).divide(listed, 0, RoundingMode.FLOOR);
                giftLine.net = paid.subtract(w.net);
                lines.add(giftLine);
            }
        }

        BigDecimal subtotal = sum(lines, l -> l.net);
        Promotion orderPromo = null;
        BigDecimal orderDiscount = BigDecimal.ZERO;
        for (Promotion p : orderPromos) {
            BigDecimal d = orderDiscount(p, subtotal);
            if (d.compareTo(orderDiscount) > 0) {
                orderPromo = p;
                orderDiscount = d;
            }
        }
        List<BigDecimal> shares = distribute(lines, orderDiscount, subtotal);

        List<PricedLine> priced = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Work w = lines.get(i);
            BigDecimal net = w.net.subtract(shares.get(i));
            PromotionSnapshot snap = w.promotion == null ? null
                    : new PromotionSnapshot(w.promotion.getId(), w.promotion.getName(), w.promotion.getType(),
                            w.gross.subtract(w.net), w.freeUnits == 0 ? null : w.freeUnits);
            priced.add(new PricedLine(i + 1, w.productId, w.sku, w.name, w.categoryId, w.quantity, w.unitPrice, w.gross,
                    net, w.gift, snap));
        }
        BigDecimal grossTotal = sum(lines, l -> l.gross);
        BigDecimal total = priced.stream().map(PricedLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        PromotionSnapshot orderSnap = orderPromo == null ? null
                : new PromotionSnapshot(orderPromo.getId(), orderPromo.getName(), orderPromo.getType(), orderDiscount, null);
        return new PricedCart(priced, orderSnap, grossTotal, grossTotal.subtract(total), total, List.copyOf(warnings),
                hash(priced, orderSnap, total));
    }

    private static Candidate evaluate(Promotion p, CartItem item, BigDecimal gross,
            Function<String, Optional<GiftProduct>> gifts, Map<String, Integer> cartQty,
            Map<String, Integer> giftsReserved, List<String> warnings) {
        Promotion.Rule r = p.getRule();
        switch (p.getType()) {
            case PERCENT:
                return new Candidate(p, money(gross.multiply(r.getPercent()).divide(HUNDRED, 0, RoundingMode.FLOOR)), 0,
                        null, 0);
            case FIXED:
                BigDecimal perUnit = r.getAmount().min(item.unitPrice());
                return new Candidate(p, money(perUnit.multiply(BigDecimal.valueOf(item.quantity()))), 0, null, 0);
            case BUY_X_GET_Y:
                int buy = r.getBuyQty();
                int get = r.getGetQty();
                if (r.getGiftProductId() == null) {
                    int free = item.quantity() / (buy + get) * get;
                    return new Candidate(p, money(item.unitPrice().multiply(BigDecimal.valueOf(free))), free, null, 0);
                }
                int giftQty = item.quantity() / buy * get;
                if (giftQty == 0) {
                    return null;
                }
                Optional<GiftProduct> gift = gifts.apply(r.getGiftProductId());
                if (gift.isEmpty()) {
                    warnings.add("Quà của khuyến mãi \"" + p.getName() + "\" không còn bán, không áp dụng");
                    return null;
                }
                GiftProduct g = gift.get();
                int needed = giftQty + cartQty.getOrDefault(g.productId(), 0) + giftsReserved.getOrDefault(g.productId(), 0);
                if (needed > g.available()) {
                    warnings.add("Quà " + g.name() + " không đủ tồn cho khuyến mãi \"" + p.getName() + "\", không áp dụng");
                    return null;
                }
                return new Candidate(p, money(g.unitPrice().multiply(BigDecimal.valueOf(giftQty))), 0, g, giftQty);
            default:
                return null;
        }
    }

    private static BigDecimal orderDiscount(Promotion p, BigDecimal subtotal) {
        Promotion.Rule r = p.getRule();
        if (subtotal.compareTo(r.getMinTotal()) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal d;
        if (r.getAmount() != null) {
            d = r.getAmount();
        } else {
            d = subtotal.multiply(r.getPercent()).divide(HUNDRED, 0, RoundingMode.FLOOR);
            if (r.getMaxDiscount() != null) {
                d = d.min(r.getMaxDiscount());
            }
        }
        return money(d.min(subtotal));
    }

    /** Chia {@code discount} theo tỉ lệ net từng dòng, làm tròn xuống; phần dư vào dòng cuối còn đủ tiền. */
    private static List<BigDecimal> distribute(List<Work> lines, BigDecimal discount, BigDecimal subtotal) {
        List<BigDecimal> shares = new ArrayList<>();
        if (discount.signum() == 0 || subtotal.signum() == 0) {
            lines.forEach(l -> shares.add(BigDecimal.ZERO));
            return shares;
        }
        BigDecimal used = BigDecimal.ZERO;
        for (Work l : lines) {
            BigDecimal s = discount.multiply(l.net).divide(subtotal, 0, RoundingMode.FLOOR);
            shares.add(s);
            used = used.add(s);
        }
        BigDecimal remainder = discount.subtract(used);
        for (int i = lines.size() - 1; i >= 0 && remainder.signum() > 0; i--) {
            BigDecimal room = lines.get(i).net.subtract(shares.get(i));
            BigDecimal add = remainder.min(room);
            shares.set(i, shares.get(i).add(add));
            remainder = remainder.subtract(add);
        }
        return shares;
    }

    private static Map<String, CartItem> merge(List<CartItem> cart) {
        Map<String, CartItem> merged = new LinkedHashMap<>();
        for (CartItem i : cart) {
            merged.merge(i.productId(), i, (a, b) -> new CartItem(a.productId(), a.sku(), a.name(), a.categoryId(),
                    a.unitPrice(), a.quantity() + b.quantity()));
        }
        return merged;
    }

    private static Work work(String productId, String sku, String name, String categoryId, int qty, BigDecimal price) {
        Work w = new Work();
        w.productId = productId;
        w.sku = sku;
        w.name = name;
        w.categoryId = categoryId;
        w.quantity = qty;
        w.unitPrice = money(price);
        w.gross = money(price.multiply(BigDecimal.valueOf(qty)));
        w.net = w.gross;
        return w;
    }

    private static BigDecimal sum(List<Work> lines, Function<Work, BigDecimal> f) {
        return lines.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(0, RoundingMode.FLOOR);
    }

    static String hash(List<PricedLine> lines, PromotionSnapshot order, BigDecimal total) {
        StringBuilder sb = new StringBuilder();
        for (PricedLine l : lines) {
            sb.append(l.productId()).append('|').append(l.quantity()).append('|').append(l.unitPrice().toPlainString())
                    .append('|').append(l.netAmount().toPlainString()).append('|').append(l.gift()).append('|')
                    .append(l.promotion() == null ? "-" : l.promotion().promotionId()).append(';');
        }
        sb.append("order:").append(order == null ? "-" : order.promotionId()).append("|total:").append(total.toPlainString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
