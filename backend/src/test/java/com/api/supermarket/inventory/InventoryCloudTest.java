package com.api.supermarket.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.common.MongoTx;
import com.api.supermarket.inventory.BatchService.ReceiveCommand;
import com.api.supermarket.security.JwtPrincipal;
import com.api.supermarket.support.CloudTest;

/** NFR-03 trên Atlas thật: hai luồng cùng lấy đơn vị hàng cuối, đúng một luồng thành công, tồn không âm. */
@CloudTest
class InventoryCloudTest {
    @Autowired
    CategoryService categories;
    @Autowired
    ProductService productService;
    @Autowired
    ProductRepository products;
    @Autowired
    BatchService batches;
    @Autowired
    FefoAllocator allocator;
    @Autowired
    MongoTx tx;
    @Autowired
    MongoTemplate mongo;

    @Test
    void lastUnitIsSoldExactlyOnce() throws Exception {
        String tag = String.valueOf(System.nanoTime() % 1_000_000);
        JwtPrincipal keeper = new JwtPrincipal("cloud", "cloudtest", "WAREHOUSE");
        Category c = categories.create(new CategoryRequest("Kho test " + tag, null, true));
        String productId = productService.create(new ProductRequest("KHO-" + tag, "Hàng test " + tag,
                new BigDecimal("1000"), c.getId(), null, 0, null, Map.of(), true, null, null, null)).id();
        Batch batch = batches.receiveLoose(new ReceiveCommand(productId, null, 1, BigDecimal.ZERO, null, null, null, null),
                keeper);
        try {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        tx.run(() -> {
                            allocator.allocate(productId, "hàng test", 1);
                            products.incStock(productId, -1, 0);
                            return null;
                        });
                        return true;
                    } catch (InsufficientStockException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Boolean> f : results) {
                ok += f.get() ? 1 : 0;
            }
            pool.shutdown();

            assertEquals(1, ok, "đúng một luồng bán được đơn vị cuối");
            assertEquals(0, mongo.findById(batch.getId(), Batch.class).getRemainingQty());
            assertEquals(0, mongo.findById(productId, Product.class).getStockQuantity());
        } finally {
            mongo.remove(Query.query(Criteria.where("_id").is(batch.getId())), Batch.class);
            mongo.remove(Query.query(Criteria.where("_id").is(productId)), Product.class);
            mongo.remove(Query.query(Criteria.where("_id").is(c.getId())), Category.class);
        }
    }
}
