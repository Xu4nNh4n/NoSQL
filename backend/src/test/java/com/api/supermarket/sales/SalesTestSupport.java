package com.api.supermarket.sales;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.promotion.Pricing.PricedLine;
import com.api.supermarket.security.JwtPrincipal;

final class SalesTestSupport {
    static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    static final AppTime TIME = new AppTime(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    static final JwtPrincipal CASHIER = new JwtPrincipal("c1", "thungan01", "STAFF");

    private SalesTestSupport() {
    }

    static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    static PricedLine line(int no, String productId, int qty, long unit, long net, boolean gift) {
        return new PricedLine(no, productId, "SKU-" + productId, "SP " + productId, "cat", qty, vnd(unit), vnd(unit * qty),
                vnd(net), gift, null);
    }

    static PricedCart cart(String hash, PricedLine... lines) {
        BigDecimal gross = List.of(lines).stream().map(PricedLine::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = List.of(lines).stream().map(PricedLine::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PricedCart(List.of(lines), null, gross, gross.subtract(total), total, List.of(), hash);
    }

    /** Đơn đã bán: dòng 1 sữa 3 hộp net 60k, cấp phát 2 lô (hạn gần 1 hộp, hạn xa 2 hộp). */
    static SalesOrder paidOrder() {
        SalesOrder o = new SalesOrder();
        o.setId("o1");
        o.setOrderCode("HD261107-0001");
        o.setCashier(new SalesOrder.PersonRef("c1", "thungan01", null));
        o.setOrderedAt(NOW);
        o.setOrderDay(TODAY);
        o.setStatus(SalesOrder.Status.PAID);
        o.setPaymentMethod(SalesOrder.Method.CASH);
        o.setTotal(vnd(60_000));
        o.setGrossTotal(vnd(90_000));
        o.setDiscountTotal(vnd(30_000));
        SalesOrder.OrderLine l = new SalesOrder.OrderLine();
        l.setLineNo(1);
        l.setProductId("milk");
        l.setSku("SUA");
        l.setName("Sữa");
        l.setQuantity(3);
        l.setUnitPrice(vnd(30_000));
        l.setGrossAmount(vnd(90_000));
        l.setNetAmount(vnd(60_000));
        l.getAllocations().add(alloc("near", TODAY.plusDays(2), 1));
        l.getAllocations().add(alloc("far", TODAY.plusDays(20), 2));
        o.getItems().add(l);
        return o;
    }

    static SalesOrder.Allocation alloc(String batchId, LocalDate expiry, int qty) {
        SalesOrder.Allocation a = new SalesOrder.Allocation();
        a.setBatchId(batchId);
        a.setBatchCode("L-" + batchId);
        a.setExpiryDate(expiry);
        a.setQuantity(qty);
        return a;
    }
}
