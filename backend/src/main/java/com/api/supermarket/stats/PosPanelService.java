package com.api.supermarket.stats;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.ProductDtos.ProductResponse;
import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.graph.GraphQueryService;
import com.api.supermarket.graph.GraphQueryService.Suggestions;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.sales.TopSalesService;
import com.api.supermarket.sales.TopSalesService.TopSales;

/**
 * Bảng thông tin sản phẩm ở quầy (spec 19.3 – đa luồng): bốn truy vấn độc lập trên ba CSDL chạy song song bằng
 * {@link CompletableFuture} trên luồng ảo. Trả kèm thời gian từng phần, tổng nếu chạy tuần tự và thời gian song song
 * thực để minh họa lợi ích khi CSDL nằm trên cloud.
 */
@Service
public class PosPanelService implements DisposableBean {
    public record Timing(String part, long millis) {
    }

    public record Panel(ProductResponse product, List<Batch> fefoBatches, Suggestions alsoBought, TopSales topToday,
            List<Timing> timings, long sequentialMillis, long parallelMillis) {
    }

    private record Timed<T>(T value, Timing timing) {
    }

    private final ProductService products;
    private final BatchRepository batches;
    private final GraphQueryService graph;
    private final TopSalesService topSales;
    private final AppTime time;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PosPanelService(ProductService products, BatchRepository batches, GraphQueryService graph,
            TopSalesService topSales, AppTime time) {
        this.products = products;
        this.batches = batches;
        this.graph = graph;
        this.topSales = topSales;
        this.time = time;
    }

    public Panel panel(String productId) {
        long start = System.nanoTime();
        var product = timed("MongoDB: sản phẩm", () -> products.get(productId));
        var lots = timed("MongoDB: lô FEFO", () -> batches.findAvailableForFefo(productId, time.today()));
        var suggestions = timed("Neo4j: gợi ý mua kèm", () -> graph.alsoBought(productId, 5));
        var top = timed("Redis: top bán hôm nay", () -> topSales.today(10));
        try {
            CompletableFuture.allOf(product, lots, suggestions, top).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException re) {
                throw re; // ví dụ 404 sản phẩm
            }
            throw ex;
        }
        long parallel = (System.nanoTime() - start) / 1_000_000;
        List<Timing> timings = List.of(product.join().timing(), lots.join().timing(), suggestions.join().timing(),
                top.join().timing());
        long sequential = timings.stream().mapToLong(Timing::millis).sum();
        return new Panel(product.join().value(), lots.join().value(), suggestions.join().value(), top.join().value(),
                timings, sequential, parallel);
    }

    private <T> CompletableFuture<Timed<T>> timed(String part, Supplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            long t0 = System.nanoTime();
            T value = work.get();
            return new Timed<>(value, new Timing(part, (System.nanoTime() - t0) / 1_000_000));
        }, executor);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
