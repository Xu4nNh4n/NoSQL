package com.api.supermarket.integration;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** MySQL only: all reads share one InnoDB repeatable-read snapshot. */
@Service
public class StockSnapshotReader {
    public record Snapshot(LocalDate date, long productId, long opening, long received,
            long sold, long returned, long adjustmentDelta, long closing) { }
    public record Issue(long productId, String reason) { }
    public record Plan(Instant generatedAt, List<Snapshot> rows, List<Issue> issues,
            long omittedBeforeHistory, long omittedUnstockedProducts) { }
    private record Totals(long received, long sold, long returned, long adjustment) {
        Totals plus(String type, long quantity) {
            return switch (type) {
                case "OPENING", "RECEIPT" -> new Totals(Math.addExact(received, quantity), sold, returned, adjustment);
                case "SALE" -> new Totals(received, Math.subtractExact(sold, quantity), returned, adjustment);
                case "RETURN" -> new Totals(received, sold, Math.addExact(returned, quantity), adjustment);
                case "ADJUSTMENT" -> new Totals(received, sold, returned, Math.addExact(adjustment, quantity));
                default -> this; // Validated RETURN_DAMAGED has no remaining-stock delta.
            };
        }
        long closing(long opening) {
            return Math.addExact(Math.addExact(Math.subtractExact(Math.addExact(opening, received), sold), returned), adjustment);
        }
    }
    private static final class ProductHistory {
        long physical;
        boolean hasMovements;
        long firstInbound = Long.MAX_VALUE;
        long firstPhysicalMovement = Long.MAX_VALUE;
        final NavigableMap<LocalDate, Totals> days = new TreeMap<>();
        final Set<String> issues = new LinkedHashSet<>();
    }
    private final JdbcTemplate jdbc;

    public StockSnapshotReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Plan read(LocalDate from, LocalDate to) {
        Map<Long, ProductHistory> products = new TreeMap<>();
        // This first table read establishes the consistent MySQL snapshot. No expiry/status filter.
        jdbc.query("SELECT p.product_id, COALESCE(SUM(b.remaining_quantity), 0) AS physical, "
                + "COALESCE(SUM(CASE WHEN b.remaining_quantity < 0 THEN 1 ELSE 0 END), 0) AS invalid_batches "
                + "FROM products p LEFT JOIN inventory_batches b ON b.product_id = p.product_id GROUP BY p.product_id",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    ProductHistory history = new ProductHistory();
                    history.physical = row.getLong("physical");
                    if (row.getLong("invalid_batches") > 0) history.issues.add("NEGATIVE_BATCH_REMAINING");
                    products.put(row.getLong("product_id"), history);
                });
        long now = Objects.requireNonNull(jdbc.queryForObject("SELECT UNIX_TIMESTAMP(CURRENT_TIMESTAMP)", Long.class));
        // UNIX_TIMESTAMP interprets MySQL TIMESTAMP correctly even when the session is not UTC.
        // +25200 maps UTC epoch seconds to Vietnam days without MySQL time-zone tables.
        jdbc.query("SELECT product_id, FLOOR((UNIX_TIMESTAMP(created_at) + 25200) / 86400) AS vn_day, "
                + "movement_type, SUM(quantity) AS quantity, "
                + "MIN(UNIX_TIMESTAMP(created_at)) AS first_time, MAX(UNIX_TIMESTAMP(created_at)) AS last_time, "
                + "SUM(CASE WHEN quantity IS NULL OR created_at IS NULL "
                + "OR (movement_type IN ('OPENING','RECEIPT','RETURN','RETURN_DAMAGED') AND quantity <= 0) "
                + "OR (movement_type = 'SALE' AND quantity >= 0) "
                + "THEN 1 ELSE 0 END) AS invalid_rows "
                + "FROM stock_movements GROUP BY product_id, vn_day, movement_type",
                (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                    ProductHistory history = products.computeIfAbsent(row.getLong("product_id"), id -> {
                        ProductHistory missing = new ProductHistory();
                        missing.issues.add("MOVEMENT_WITHOUT_PRODUCT");
                        return missing;
                    });
                    history.hasMovements = true;
                    String type = row.getString("movement_type");
                    if (type == null || !Set.of("OPENING", "RECEIPT", "SALE", "RETURN", "RETURN_DAMAGED", "ADJUSTMENT").contains(type)) {
                        history.issues.add("UNSUPPORTED_MOVEMENT_TYPE: " + type);
                        return;
                    }
                    if (row.getLong("invalid_rows") > 0) history.issues.add("INVALID_MOVEMENT: " + type);
                    if (row.getObject("vn_day") == null) { history.issues.add("MISSING_MOVEMENT_DATE"); return; }
                    if (row.getLong("last_time") > now) history.issues.add("FUTURE_MOVEMENT");
                    long first = row.getLong("first_time");
                    if ("OPENING".equals(type) || "RECEIPT".equals(type)) history.firstInbound = Math.min(history.firstInbound, first);
                    if (!"RETURN_DAMAGED".equals(type)) history.firstPhysicalMovement = Math.min(history.firstPhysicalMovement, first);
                    LocalDate day = LocalDate.ofEpochDay(row.getLong("vn_day"));
                    Totals totals = history.days.getOrDefault(day, new Totals(0, 0, 0, 0));
                    history.days.put(day, totals.plus(type, row.getLong("quantity")));
                });
        List<Snapshot> snapshots = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        long omitted = 0;
        long omittedUnstocked = 0;
        for (var entry : products.entrySet()) {
            long id = entry.getKey();
            ProductHistory history = entry.getValue();
            if (!history.hasMovements && history.physical == 0 && history.issues.isEmpty()) {
                omittedUnstocked++;
                continue;
            }
            if (history.firstInbound == Long.MAX_VALUE) history.issues.add("NO_KNOWN_OPENING_OR_RECEIPT");
            else if (history.firstPhysicalMovement < history.firstInbound) history.issues.add("MOVEMENT_BEFORE_OPENING_OR_RECEIPT");
            long balance = 0;
            for (Totals totals : history.days.values()) {
                balance = totals.closing(balance);
                if (balance < 0) history.issues.add("NEGATIVE_RECONSTRUCTED_DAILY_BALANCE");
            }
            if (balance != history.physical) history.issues.add("UNEXPLAINED_BALANCE: ledger=" + balance + ", batches=" + history.physical);
            if (!history.issues.isEmpty()) {
                history.issues.forEach(reason -> issues.add(new Issue(id, reason)));
                continue;
            }
            LocalDate origin = Instant.ofEpochSecond(history.firstInbound).atZone(ReportTime.ZONE).toLocalDate();
            long opening = 0;
            for (Totals totals : history.days.headMap(from, false).values()) opening = totals.closing(opening);
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                if (day.isBefore(origin)) { omitted++; continue; }
                Totals totals = history.days.getOrDefault(day, new Totals(0, 0, 0, 0));
                long closing = totals.closing(opening);
                snapshots.add(new Snapshot(day, id, opening, totals.received(), totals.sold(), totals.returned(), totals.adjustment(), closing));
                opening = closing;
            }
        }
        return new Plan(Instant.ofEpochSecond(now), List.copyOf(snapshots), List.copyOf(issues), omitted, omittedUnstocked);
    }
}
