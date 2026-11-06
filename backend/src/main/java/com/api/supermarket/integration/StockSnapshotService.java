package com.api.supermarket.integration;

import com.api.supermarket.exception.AnalyticsUnavailableException;
import com.api.supermarket.exception.BadRequestException;
import com.datastax.oss.driver.api.core.CqlSession;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Explicit admin invocation only; no scheduler, DDL or MySQL writes. */
@Service
public class StockSnapshotService {
    private final StockSnapshotReader reader;
    private final ObjectProvider<CqlSession> sessions;

    public StockSnapshotService(StockSnapshotReader reader, ObjectProvider<CqlSession> sessions) {
        this.reader = reader;
        this.sessions = sessions;
    }

    public synchronized Map<String, Object> generate(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 31)
            throw new BadRequestException("Snapshot interval must contain 1 to 31 Vietnam dates");
        if (!to.isBefore(ReportTime.today()))
            throw new BadRequestException("Only completed Vietnam dates can have a closing-stock snapshot");
        StockSnapshotReader.Plan plan = reader.read(from, to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("timeZone", ReportTime.ZONE.getId());
        result.put("generatedAt", plan.generatedAt().toString());
        result.put("omittedBeforeHistory", plan.omittedBeforeHistory());
        result.put("omittedUnstockedProducts", plan.omittedUnstockedProducts());
        result.put("issues", plan.issues());
        result.put("written", 0);
        if (!plan.issues().isEmpty()) {
            result.put("status", "REJECTED");
            result.put("reason", "History cannot be reconstructed and reconciled; no Cassandra writes attempted");
            return result;
        }
        if (plan.rows().isEmpty()) {
            result.put("status", "NO_DATA");
            result.put("reason", "No requested product-days have a known opening or receipt");
            return result;
        }
        int written = 0;
        try {
            CqlSession session = sessions.getIfAvailable();
            if (session == null) throw new AnalyticsUnavailableException("Cassandra is disabled; no snapshots written");
            var insert = session.prepare("INSERT INTO stock_daily_by_day "
                    + "(snapshot_date, product_id, remaining_quantity, opening, received, sold, returned, adjustment_delta, closing, generated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            for (var row : plan.rows()) {
                session.execute(insert.boundStatementBuilder()
                        .setLocalDate("snapshot_date", row.date()).setLong("product_id", row.productId())
                        .setLong("remaining_quantity", row.closing()).setLong("opening", row.opening())
                        .setLong("received", row.received()).setLong("sold", row.sold())
                        .setLong("returned", row.returned()).setLong("adjustment_delta", row.adjustmentDelta())
                        .setLong("closing", row.closing()).setInstant("generated_at", plan.generatedAt()).build());
                written++;
            }
        } catch (RuntimeException ex) {
            // Cassandra writes cannot roll back with MySQL. A timeout may have applied its last row.
            throw new AnalyticsUnavailableException("Stock snapshot write failed (" + ex.getClass().getSimpleName()
                    + "); acknowledged rows=" + written + ". An additional row may have applied; rerun the same interval after resolving the failure");
        }
        result.put("status", "WRITTEN");
        result.put("written", written);
        return result;
    }
}
