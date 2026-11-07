package com.api.supermarket.sales;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.config.CassandraSessionProvider;
import com.api.supermarket.inventory.InventoryEvents;
import com.api.supermarket.sync.SyncHandler;
import com.api.supermarket.sync.SyncTask;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

/**
 * Sổ thanh toán trên Cassandra. Mỗi khoản ghi vào hai bảng, mỗi bảng phục vụ một câu truy vấn:
 * {@code payments_by_order} (AP-14) và {@code payments_by_cashier_day} (AP-15, chốt ca). Hai lệnh nằm trong một
 * BATCH logged nên hai bảng không lệch nhau; {@code payment_id} xác định nên ghi lặp an toàn.
 * Lớp này cũng là handler đồng bộ cho ORDER_CREATED và RETURN_CREATED.
 */
@Component
public class PaymentLedger implements SyncHandler {
    private record Statements(CqlSession session, PreparedStatement insertByOrder, PreparedStatement insertByCashier,
            PreparedStatement selectByOrder, PreparedStatement selectByCashier) {
    }

    private final CassandraSessionProvider cassandra;
    private final AppTime time;
    private volatile Statements statements;

    public PaymentLedger(CassandraSessionProvider cassandra, AppTime time) {
        this.cassandra = cassandra;
        this.time = time;
    }

    @Override
    public Set<String> types() {
        return Set.of(InventoryEvents.ORDER_CREATED, InventoryEvents.RETURN_CREATED);
    }

    @Override
    public void apply(SyncTask task) {
        write(SalesEvents.payments(task.getPayload()));
    }

    public void write(List<LedgerPayment> payments) {
        if (payments.isEmpty()) {
            return;
        }
        Statements s = statements();
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);
        for (LedgerPayment p : payments) {
            LocalDate day = p.createdAt().atZone(time.zone()).toLocalDate();
            batch.addStatement(s.insertByOrder().bind(p.orderId(), p.createdAt(), p.paymentId(), p.type(), p.method(),
                    p.amount(), p.reference(), p.returnCode(), p.cashierId()));
            batch.addStatement(s.insertByCashier().bind(p.cashierId(), day, p.createdAt(), p.paymentId(), p.orderCode(),
                    p.type(), p.method(), p.amount()));
        }
        s.session().execute(batch.build());
    }

    public List<LedgerPayment> byOrder(String orderId) {
        try {
            Statements s = statements();
            List<LedgerPayment> result = new ArrayList<>();
            for (Row r : s.session().execute(s.selectByOrder().bind(orderId))) {
                result.add(new LedgerPayment(r.getUuid("payment_id"), orderId, null, r.getString("type"),
                        r.getString("method"), r.getBigDecimal("amount"), r.getString("reference"),
                        r.getString("return_code"), r.getString("cashier_id"), r.getInstant("created_at")));
            }
            return result;
        } catch (DatastoreUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DatastoreUnavailableException("cassandra", ex);
        }
    }

    public List<LedgerPayment> byCashierDay(String cashierId, LocalDate day) {
        try {
            Statements s = statements();
            List<LedgerPayment> result = new ArrayList<>();
            for (Row r : s.session().execute(s.selectByCashier().bind(cashierId, day))) {
                result.add(new LedgerPayment(r.getUuid("payment_id"), null, r.getString("order_code"), r.getString("type"),
                        r.getString("method"), r.getBigDecimal("amount"), null, null, cashierId,
                        r.getInstant("created_at")));
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
                session.prepare("INSERT INTO payments_by_order (order_id, created_at, payment_id, type, method, amount, "
                        + "reference, return_code, cashier_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                session.prepare("INSERT INTO payments_by_cashier_day (cashier_id, day, created_at, payment_id, order_code, "
                        + "type, method, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"),
                session.prepare("SELECT created_at, payment_id, type, method, amount, reference, return_code, cashier_id "
                        + "FROM payments_by_order WHERE order_id = ?"),
                session.prepare("SELECT created_at, payment_id, order_code, type, method, amount "
                        + "FROM payments_by_cashier_day WHERE cashier_id = ? AND day = ?"));
        statements = fresh;
        return fresh;
    }
}
