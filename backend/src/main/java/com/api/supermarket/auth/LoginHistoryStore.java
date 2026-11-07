package com.api.supermarket.auth;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.DatastoreUnavailableException;
import com.api.supermarket.config.CassandraSessionProvider;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

/**
 * Lịch sử đăng nhập trên Cassandra, bảng {@code login_history_by_user} (AP-16):
 * partition (user_id, month), sắp mới nhất trước, TTL mặc định 90 ngày của bảng tự xóa dữ liệu cũ.
 * Ghi chạy nền để Cassandra chậm hoặc lỗi không làm chậm đăng nhập.
 */
@Component
public class LoginHistoryStore {
    private static final Logger log = LoggerFactory.getLogger(LoginHistoryStore.class);
    private static final int MAX_USER_AGENT = 300;
    private static final int MAX_ROWS = 200;

    public record LoginEvent(Instant at, boolean success, String ip, String userAgent) {
    }

    private record Statements(CqlSession session, PreparedStatement insert, PreparedStatement select) {
    }

    private final CassandraSessionProvider cassandra;
    private final TaskExecutor executor;
    private final AppTime time;
    private volatile Statements statements;

    public LoginHistoryStore(CassandraSessionProvider cassandra, @Qualifier("syncExecutor") TaskExecutor executor,
            AppTime time) {
        this.cassandra = cassandra;
        this.executor = executor;
        this.time = time;
    }

    public void record(String userId, boolean success, String ip, String userAgent) {
        Instant at = time.now();
        String agent = userAgent == null || userAgent.length() <= MAX_USER_AGENT ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT);
        try {
            executor.execute(() -> {
                try {
                    Statements s = statements();
                    s.session().execute(s.insert().bind(userId, month(at), at, UUID.randomUUID(), success, ip, agent));
                } catch (RuntimeException ex) {
                    log.warn("Không ghi được lịch sử đăng nhập của {}: {}", userId, ex.toString());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Hàng đợi nền đầy, bỏ qua ghi lịch sử đăng nhập: {}", ex.toString());
        }
    }

    public List<LoginEvent> list(String userId, YearMonth month) {
        try {
            Statements s = statements();
            List<LoginEvent> result = new ArrayList<>();
            for (Row row : s.session().execute(s.select().bind(userId, month.toString()))) {
                result.add(new LoginEvent(row.getInstant("login_at"), row.getBoolean("success"),
                        row.getString("ip"), row.getString("user_agent")));
            }
            return result;
        } catch (DatastoreUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DatastoreUnavailableException("cassandra", ex);
        }
    }

    private String month(Instant at) {
        return YearMonth.from(at.atZone(time.zone())).toString();
    }

    /** Prepared statement gắn với session; kết nối lại thì chuẩn bị lại. */
    private Statements statements() {
        CqlSession session = cassandra.session();
        Statements current = statements;
        if (current != null && current.session() == session) {
            return current;
        }
        Statements fresh = new Statements(session,
                session.prepare("INSERT INTO login_history_by_user (user_id, month, login_at, event_id, success, ip, user_agent) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)"),
                session.prepare("SELECT login_at, success, ip, user_agent FROM login_history_by_user "
                        + "WHERE user_id = ? AND month = ? LIMIT " + MAX_ROWS));
        statements = fresh;
        return fresh;
    }
}
