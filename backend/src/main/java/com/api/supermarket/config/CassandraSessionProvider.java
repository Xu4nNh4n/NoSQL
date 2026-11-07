package com.api.supermarket.config;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.api.supermarket.common.DatastoreUnavailableException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

/**
 * Kết nối Cassandra tạo lười: chỉ mở khi lần đầu cần dùng, nên ứng dụng vẫn khởi động
 * khi Cassandra tắt (NFR-09). Lần trước lỗi thì lần sau thử kết nối lại.
 */
@Component
public class CassandraSessionProvider implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(CassandraSessionProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String mode;
    private final String bundle;
    private final String token;
    private final String keyspace;
    private final String localContact;
    private final String localDc;

    private volatile CqlSession session;

    public CassandraSessionProvider(
            @Value("${app.cassandra.mode:ASTRA}") String mode,
            @Value("${app.cassandra.bundle:}") String bundle,
            @Value("${app.cassandra.token:}") String token,
            @Value("${app.cassandra.keyspace:supermarket}") String keyspace,
            @Value("${app.cassandra.local-contact:127.0.0.1:9042}") String localContact,
            @Value("${app.cassandra.local-dc:datacenter1}") String localDc) {
        this.mode = mode;
        this.bundle = bundle;
        this.token = token;
        this.keyspace = keyspace;
        this.localContact = localContact;
        this.localDc = localDc;
    }

    public String keyspace() {
        return keyspace;
    }

    public CqlSession session() {
        CqlSession current = session;
        if (current != null && !current.isClosed()) {
            return current;
        }
        synchronized (this) {
            if (session == null || session.isClosed()) {
                session = connect();
            }
            return session;
        }
    }

    private CqlSession connect() {
        try {
            if (isLocal()) {
                createLocalKeyspace();
            }
            return builder().withKeyspace(keyspace).build();
        } catch (RuntimeException ex) {
            throw new DatastoreUnavailableException("cassandra", ex);
        }
    }

    private boolean isLocal() {
        return "LOCAL".equalsIgnoreCase(mode);
    }

    /** Cassandra cài máy (chỉ dùng thử): tự tạo keyspace 1 bản sao. Astra thì tạo trên giao diện. */
    private void createLocalKeyspace() {
        try (CqlSession admin = builder().build()) {
            admin.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspace
                    + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        }
    }

    private CqlSessionBuilder builder() {
        DriverConfigLoader config = DriverConfigLoader.programmaticBuilder()
                .withDuration(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, TIMEOUT)
                .withDuration(DefaultDriverOption.CONNECTION_CONNECT_TIMEOUT, TIMEOUT)
                .withDuration(DefaultDriverOption.CONTROL_CONNECTION_TIMEOUT, TIMEOUT)
                .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, TIMEOUT)
                // R + W > RF (spec mục 19.5)
                .withString(DefaultDriverOption.REQUEST_CONSISTENCY, "LOCAL_QUORUM")
                .build();
        CqlSessionBuilder builder = CqlSession.builder().withConfigLoader(config);
        if (isLocal()) {
            String[] hostPort = localContact.split(":");
            return builder
                    .addContactPoint(new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])))
                    .withLocalDatacenter(localDc);
        }
        return builder
                .withCloudSecureConnectBundle(Path.of(bundle))
                .withAuthCredentials("token", token);
    }

    @Override
    public void destroy() {
        CqlSession current = session;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException ex) {
                log.debug("Đóng session Cassandra lỗi: {}", ex.toString());
            }
        }
    }
}
