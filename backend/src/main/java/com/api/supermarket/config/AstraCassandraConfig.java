package com.api.supermarket.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.net.InetSocketAddress;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import org.springframework.context.annotation.Lazy;

@Configuration
@ConditionalOnProperty(name = "astra.enabled", havingValue = "true")
public class AstraCassandraConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    public CqlSession astraCqlSession(
            @Value("${astra.secure-connect-bundle}") String secureConnectBundle,
            @Value("${astra.application-token}") String applicationToken,
            @Value("${astra.keyspace}") String keyspace,
            @Value("${astra.mode:LOCAL}") String mode,
            @Value("${astra.host:127.0.0.1}") String host,
            @Value("${astra.port:9042}") int port,
            @Value("${astra.local-datacenter:datacenter1}") String datacenter) {
        var builder = CqlSession.builder()
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(10))
                        .build())
                .withKeyspace(keyspace);
        if ("LOCAL".equalsIgnoreCase(mode)) {
            if (host.isBlank() || datacenter.isBlank() || port < 1 || port > 65535) {
                throw new IllegalStateException("Cấu hình host/port/datacenter Cassandra local không hợp lệ");
            }
            return builder.addContactPoint(new InetSocketAddress(host, port))
                    .withLocalDatacenter(datacenter).build();
        }
        if (!"ASTRA".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("CASSANDRA_MODE chỉ nhận LOCAL hoặc ASTRA");
        }
        if (secureConnectBundle.isBlank()) {
            throw new IllegalStateException("ASTRA_SECURE_BUNDLE chưa được cấu hình");
        }
        if (applicationToken.isBlank()) {
            throw new IllegalStateException("APPLICATION_TOKEN chưa được cấu hình");
        }
        Path bundle = Path.of(secureConnectBundle);
        if (!Files.isRegularFile(bundle)) {
            throw new IllegalStateException("Không tìm thấy file zip ASTRA_SECURE_BUNDLE");
        }

        return builder
                .withCloudSecureConnectBundle(bundle)
                .withAuthCredentials("token", applicationToken)
                .build();
    }
}
