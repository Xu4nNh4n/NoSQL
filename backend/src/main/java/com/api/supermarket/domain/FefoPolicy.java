package com.api.supermarket.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Pure business policy; storage adapters must lock balances before applying its plan. */
public final class FefoPolicy {
    private FefoPolicy() {}
    public record Lot(Long id, int available, LocalDate expiryDate, LocalDateTime receivedAt) {}
    public record Pick(Long lotId, int quantity) {}

    public static List<Pick> allocate(List<Lot> lots, int quantity, LocalDate today) {
        if (quantity <= 0) throw new IllegalArgumentException("Số lượng phải dương");
        List<Lot> eligible = lots.stream()
                .filter(lot -> lot.available() > 0
                        && (lot.expiryDate() == null || !lot.expiryDate().isBefore(today)))
                .sorted(Comparator.comparing(Lot::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Lot::receivedAt).thenComparing(Lot::id)).toList();
        int remaining = quantity;
        List<Pick> result = new ArrayList<>();
        for (Lot lot : eligible) {
            int taken = Math.min(remaining, lot.available());
            result.add(new Pick(lot.id(), taken));
            remaining -= taken;
            if (remaining == 0) return List.copyOf(result);
        }
        throw new IllegalArgumentException("Không đủ hàng còn hạn trong các lô khả dụng");
    }
}
