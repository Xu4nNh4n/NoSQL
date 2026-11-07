package com.api.supermarket.inventory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.RetryableConflictException;
import com.api.supermarket.domain.FefoPolicy;

/**
 * Cấp phát FEFO trên MongoDB (FR-07 bước 4). Chạy bên trong transaction của người gọi:
 * đọc lô còn hạn theo index {@code product_fefo}, lập kế hoạch bằng {@link FefoPolicy}, rồi trừ từng lô
 * bằng cập nhật có điều kiện {@code remainingQty >= số lấy}. Một lô bị đơn khác lấy trước thì ném
 * {@link RetryableConflictException} để MongoTx chạy lại cả giao dịch.
 */
@Component
public class FefoAllocator {
    public record Allocation(String batchId, String batchCode, LocalDate expiryDate, int quantity) {
    }

    private final BatchRepository batches;
    private final AppTime time;

    public FefoAllocator(BatchRepository batches, AppTime time) {
        this.batches = batches;
        this.time = time;
    }

    public List<Allocation> allocate(String productId, String productLabel, int quantity) {
        List<Batch> lots = batches.findAvailableForFefo(productId, time.today());
        Map<String, Batch> byId = lots.stream().collect(Collectors.toMap(Batch::getId, Function.identity()));
        List<FefoPolicy.Pick> picks;
        try {
            picks = FefoPolicy.allocate(lots.stream()
                    .map(b -> new FefoPolicy.Lot(b.getId(), b.getBatchCode(), b.getRemainingQty(),
                            b.getExpiryDate(), b.getReceivedAt()))
                    .toList(), quantity, time.today());
        } catch (IllegalArgumentException ex) {
            int available = lots.stream().mapToInt(Batch::getRemainingQty).sum();
            throw new InsufficientStockException("Không đủ hàng còn hạn cho " + productLabel + ": cần " + quantity
                    + ", còn " + available);
        }
        List<Allocation> result = new ArrayList<>();
        for (FefoPolicy.Pick pick : picks) {
            if (!batches.decrementIfEnough(pick.lotId(), pick.quantity())) {
                throw new RetryableConflictException("Lô vừa được bán cho đơn khác");
            }
            Batch b = byId.get(pick.lotId());
            result.add(new Allocation(b.getId(), b.getBatchCode(), b.getExpiryDate(), pick.quantity()));
        }
        return result;
    }
}
