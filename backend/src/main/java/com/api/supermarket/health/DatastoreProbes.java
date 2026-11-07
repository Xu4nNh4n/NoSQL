package com.api.supermarket.health;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.api.supermarket.config.CassandraSessionProvider;

/** Bốn probe, theo thứ tự hiển thị trên trang trạng thái. */
@Configuration
public class DatastoreProbes {

    @Bean
    @Order(1)
    DatastoreProbe mongoProbe(MongoTemplate mongo) {
        return probe("MongoDB", "Nguồn sự thật: đơn hàng, tồn kho, danh mục",
                () -> mongo.executeCommand("{ ping: 1 }"));
    }

    @Bean
    @Order(2)
    DatastoreProbe cassandraProbe(CassandraSessionProvider cassandra) {
        return probe("Cassandra", "Sổ cái: thẻ kho, thanh toán, lịch sử đăng nhập",
                () -> cassandra.session().execute("SELECT release_version FROM system.local"));
    }

    @Bean
    @Order(3)
    DatastoreProbe neo4jProbe(ObjectProvider<Driver> driver) {
        return probe("Neo4j", "Đồ thị: truy vết lô, gợi ý mua kèm", () -> {
            Driver neo4j = driver.getIfAvailable();
            if (neo4j == null) {
                throw new IllegalStateException("not configured");
            }
            neo4j.verifyConnectivity();
        });
    }

    @Bean
    @Order(4)
    DatastoreProbe redisProbe(StringRedisTemplate redis) {
        return probe("Redis", "Dữ liệu tạm: phiên đăng nhập, cache, đơn treo, top bán", () -> {
            String pong = redis.execute(connection -> connection.ping(), true);
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("unexpected reply " + pong);
            }
        });
    }

    @FunctionalInterface
    interface Ping {
        void run() throws Exception;
    }

    static DatastoreProbe probe(String name, String role, Ping ping) {
        return new DatastoreProbe() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String role() {
                return role;
            }

            @Override
            public void ping() throws Exception {
                ping.run();
            }
        };
    }
}
