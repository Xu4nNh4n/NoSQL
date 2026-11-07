package com.api.supermarket.inventory;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.config.CassandraSessionProvider;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

/**
 * Thẻ kho trên Cassandra (AP-13): bảng {@code stock_movements_by_product}, partition
 * {@code (product_id, month)} để partition không phình quá 100 MB (NFR-12), sắp mới nhất trước.
 * Chỉ thêm; khóa {@code movement_id} xác định nên ghi lặp chỉ ghi đè đúng dòng cũ, không cần LWT.
 */
@Component
public class StockLedger {
    static final int MAX_ROWS = 500;

    private record Statements(CqlSession session, PreparedStatement insert, PreparedStatement select) {
    }

    private final CassandraSessionProvider cassandra;
    private final AppTime time;
    private volatile Statements statements;

    public StockLedger(CassandraSessionProvider cassandra, AppTime time) {
        this.cassandra = cassandra;
        this.time = time;
    }

    public static String month(Instant at, ZoneId zone) {
        return YearMonth.from(at.atZone(zone)).toString();
    }

    /** Lỗi được ném ra để sync_queue giữ PENDING và thử lại. */
    public void write(List<StockMovement> movements) {
        if (movements.isEmpty()) {
            return;
        }
        Statements s = statements();
        for (StockMovement m : movements) {
            s.session().execute(s.insert().bind(m.productId(), month(m.createdAt(), time.zone()), m.createdAt(), m.id(),
                    m.type(), m.quantity(), m.batchCode(), m.refType(), m.refCode(), m.note(), m.performedBy()));
        }
    }

    public List<StockMovement> read(String productId, YearMonth month) {
        try {
            Statements s = statements();
            List<StockMovement> result = new ArrayList<>();
            for (Row r : s.session().execute(s.select().bind(productId, month.toString()))) {
                result.add(new StockMovement(r.getUuid("movement_id"), productId, r.getInstant("created_at"),
                        r.getString("movement_type"), r.getInt("quantity"), r.getString("batch_code"),
                        r.getString("ref_type"), r.getString("ref_code"), r.getString("note"),
                        r.getString("performed_by")));
            }
            return result;
        } catch (DatastoreUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DatastoreUnavailableException("cassandra", ex);
        }
    }

    private Statements statements() {
        CqlSession session = cassandra.session();
        Statements current = statements;
        if (current != null && current.session() == session) {
            return current;
        }
        Statements fresh = new Statements(session,
                session.prepare("INSERT INTO stock_movements_by_product (product_id, month, created_at, movement_id, "
                        + "movement_type, quantity, batch_code, ref_type, ref_code, note, performed_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                session.prepare("SELECT created_at, movement_id, movement_type, quantity, batch_code, ref_type, ref_code, "
                        + "note, performed_by FROM stock_movements_by_product WHERE product_id = ? AND month = ? LIMIT "
                        + MAX_ROWS));
        statements = fresh;
        return fresh;
    }
}
