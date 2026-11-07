package com.api.supermarket.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Quy tắc FEFO thuần Java: hạn dùng tăng dần (lô không hạn xếp cuối) → ngày nhận → mã lô.
 * Lô hết hạn đúng hôm nay vẫn được lấy. Không đủ thì từ chối cả yêu cầu, không cấp phát một phần.
 * Lớp lưu trữ phải trừ tồn có điều kiện khi áp dụng kế hoạch này.
 */
public final class FefoPolicy {
    private FefoPolicy() {
    }

    public record Lot(String id, String code, int available, LocalDate expiryDate, Instant receivedAt) {
    }

    public record Pick(String lotId, int quantity) {
    }

    public static List<Pick> allocate(List<Lot> lots, int quantity, LocalDate today) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải dương");
        }
        List<Lot> eligible = lots.stream()
                .filter(lot -> lot.available() > 0 && (lot.expiryDate() == null || !lot.expiryDate().isBefore(today)))
                .sorted(Comparator.comparing(Lot::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Lot::receivedAt).thenComparing(Lot::code))
                .toList();
        int remaining = quantity;
        List<Pick> result = new ArrayList<>();
        for (Lot lot : eligible) {
            int taken = Math.min(remaining, lot.available());
            result.add(new Pick(lot.id(), taken));
            remaining -= taken;
            if (remaining == 0) {
                return List.copyOf(result);
            }
        }
        throw new IllegalArgumentException("Không đủ hàng còn hạn trong các lô khả dụng");
    }
}
