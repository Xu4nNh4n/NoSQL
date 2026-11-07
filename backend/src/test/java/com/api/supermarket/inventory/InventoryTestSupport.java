package com.api.supermarket.inventory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.supplier.Supplier;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.security.JwtPrincipal;

/** Dữ liệu mẫu dùng chung cho test kho. Hôm nay = 2026-11-07 giờ Việt Nam. */
final class InventoryTestSupport {
    static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 7);
    static final AppTime TIME = new AppTime(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
    static final JwtPrincipal KEEPER = new JwtPrincipal("w1", "thukho01", "WAREHOUSE");

    private InventoryTestSupport() {
    }

    static Product product(String id, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setSku("SKU-" + id);
        p.setName("Sản phẩm " + id);
        p.setActive(active);
        p.setSupplierId("s1");
        return p;
    }

    static Supplier supplier() {
        Supplier s = new Supplier();
        s.setId("s1");
        s.setName("Vinamilk");
        s.setActive(true);
        return s;
    }

    static Batch batch(String id, String productId, int remaining, LocalDate expiry) {
        Batch b = new Batch();
        b.setId(id);
        b.setBatchCode("L-" + id);
        b.setProductId(productId);
        b.setRemainingQty(remaining);
        b.setExpiryDate(expiry);
        b.setExpirySortKey(expiry == null ? Batch.NO_EXPIRY : expiry);
        b.setReceivedAt(NOW.minusSeconds(3600));
        b.setUnitCost(new BigDecimal("20000"));
        return b;
    }
}
