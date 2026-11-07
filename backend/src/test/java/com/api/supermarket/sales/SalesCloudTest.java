package com.api.supermarket.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryService;
import com.api.supermarket.catalog.category.CategoryService.CategoryRequest;
import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductDtos.ProductRequest;
import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.BatchService;
import com.api.supermarket.inventory.BatchService.ReceiveCommand;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.sales.OrderService.CreateOrderRequest;
import com.api.supermarket.sales.QuoteService.QuoteItem;
import com.api.supermarket.sales.ReturnService.ReturnItemRequest;
import com.api.supermarket.sales.ReturnService.ReturnRequest;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.shift.ShiftService;
import com.api.supermarket.support.CloudTest;
import com.api.supermarket.sync.SyncService;

/** Vòng bán → trả → chốt ca trên MongoDB + Cassandra thật (NFR-04, NFR-05, NFR-06). */
@CloudTest
class SalesCloudTest {
    @Autowired CategoryService categories;
    @Autowired ProductService productService;
    @Autowired BatchService batches;
    @Autowired QuoteService quotes;
    @Autowired OrderService orders;
    @Autowired ReturnService returns;
    @Autowired PaymentLedger ledger;
    @Autowired ShiftService shifts;
    @Autowired SyncService sync;
    @Autowired MongoTemplate mongo;

    @Test
    void sellReturnAndCloseShift() {
        String tag = String.valueOf(System.nanoTime() % 1_000_000);
        JwtPrincipal cashier = new JwtPrincipal("cloud-cashier-" + tag, "cloudcashier", "STAFF");
        Category c = categories.create(new CategoryRequest("Bán test " + tag, null, true));
        String productId = productService.create(new ProductRequest("BAN-" + tag, "Hàng bán " + tag,
                new BigDecimal("15000"), c.getId(), null, 0, null, Map.of(), true, null, null, null)).id();
        Batch batch = batches.receiveLoose(new ReceiveCommand(productId, null, 10, BigDecimal.ZERO, null, null, null, null),
                cashier);
        String key = UUID.randomUUID().toString();
        try {
            List<QuoteItem> items = List.of(new QuoteItem(productId, 3));
            PricedCart q = quotes.quote(items);
            CreateOrderRequest req = new CreateOrderRequest(key, items, SalesOrder.Method.CASH, null, q.quoteHash());
            SalesOrder order = orders.create(req, cashier);
            assertEquals(order.getId(), orders.create(req, cashier).getId(), "gửi lặp trả về đúng đơn cũ");
            assertEquals(7, mongo.findById(productId, Product.class).getStockQuantity());

            returns.create(order.getId(), new ReturnRequest(UUID.randomUUID().toString(),
                    List.of(new ReturnItemRequest(1, 1, true, "test")), SalesOrder.Method.CASH, null), cashier);
            assertEquals(8, mongo.findById(productId, Product.class).getStockQuantity());

            sync.processPendingForOwner(cashier.userId());
            assertEquals(2, ledger.byOrder(order.getId()).size(), "Cassandra có PAYMENT và REFUND");
            ShiftService.ShiftSummary s = shifts.today(cashier);
            assertEquals(new BigDecimal("45000"), s.cashIn());
            assertEquals(new BigDecimal("15000"), s.cashRefund());
            assertTrue(s.expectedCash().compareTo(new BigDecimal("30000")) == 0);
        } finally {
            mongo.remove(Query.query(Criteria.where("requestKey").is(key)), SalesOrder.class);
            mongo.remove(Query.query(Criteria.where("_id").is(batch.getId())), Batch.class);
            mongo.remove(Query.query(Criteria.where("_id").is(productId)), Product.class);
            mongo.remove(Query.query(Criteria.where("_id").is(c.getId())), Category.class);
        }
    }
}
