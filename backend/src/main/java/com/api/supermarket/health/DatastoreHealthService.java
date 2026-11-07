package com.api.supermarket.health;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Kiểm tra song song 4 CSDL, mỗi CSDL tối đa 3 giây, nên một CSDL treo không làm chậm
 * cả trang. Lỗi chỉ trả tên lớp và câu ngắn, không lộ chuỗi kết nối.
 */
@Service
public class DatastoreHealthService implements DisposableBean {
    private static final int MAX_ERROR_LENGTH = 160;

    public record DatastoreStatus(String name, String role, boolean up, long latencyMs, String error) {
    }

    private final List<DatastoreProbe> probes;
    private final Duration timeout;
    // Luồng ảo: rẻ, không giữ RAM khi probe bị treo chờ mạng.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public DatastoreHealthService(List<DatastoreProbe> probes) {
        this(probes, Duration.ofSeconds(3));
    }

    DatastoreHealthService(List<DatastoreProbe> probes, Duration timeout) {
        this.probes = probes;
        this.timeout = timeout;
    }

    public List<DatastoreStatus> check() {
        List<CompletableFuture<DatastoreStatus>> futures = probes.stream()
                .map(probe -> CompletableFuture.supplyAsync(() -> run(probe), executor)
                        .completeOnTimeout(new DatastoreStatus(probe.name(), probe.role(), false,
                                timeout.toMillis(), "timeout"), timeout.toMillis(), TimeUnit.MILLISECONDS))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private static DatastoreStatus run(DatastoreProbe probe) {
        long start = System.nanoTime();
        try {
            probe.ping();
            return new DatastoreStatus(probe.name(), probe.role(), true, elapsedMs(start), null);
        } catch (Exception ex) {
            return new DatastoreStatus(probe.name(), probe.role(), false, elapsedMs(start), describe(ex));
        }
    }

    static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? "" : root.getMessage().lines().findFirst().orElse("");
        message = message.replaceAll("\\S+://\\S+", "<uri>");
        String text = root.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH) + "…";
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
