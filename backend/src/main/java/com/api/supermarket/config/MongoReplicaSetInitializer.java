package com.api.supermarket.config;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

/**
 * MongoDB cài trên máy (chỉ để chạy thử): transaction cần replica set, nên khởi tạo
 * replica set một node nếu mongod đã bật {@code replication.replSetName} nhưng chưa initiate.
 * Atlas đã là replica set nên không bật cờ này.
 */
@Component
@Order(-1) // trước index, validator và seeder
@ConditionalOnProperty(name = "app.mongo.init-replset", havingValue = "true")
public class MongoReplicaSetInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MongoReplicaSetInitializer.class);
    private static final int NO_REPLICATION_ENABLED = 76;
    private static final int NOT_YET_INITIALIZED = 94;

    private final MongoClient client;
    private final String replSetName;
    private final String host;

    public MongoReplicaSetInitializer(MongoClient client,
            @Value("${app.mongo.replset-name:rs0}") String replSetName,
            @Value("${app.mongo.replset-host:127.0.0.1:27017}") String host) {
        this.client = client;
        this.replSetName = replSetName;
        this.host = host;
    }

    @Override
    public void run(ApplicationArguments args) {
        MongoDatabase admin = client.getDatabase("admin");
        try {
            admin.runCommand(new Document("replSetGetStatus", 1));
            log.info("MongoDB đã là replica set");
            return;
        } catch (MongoCommandException ex) {
            if (ex.getErrorCode() == NO_REPLICATION_ENABLED) {
                log.warn("MongoDB đang chạy standalone nên không có transaction. Chạy scripts/mongo-local-replset.ps1 (quyền Administrator) rồi khởi động lại app.");
                return;
            }
            if (ex.getErrorCode() != NOT_YET_INITIALIZED) {
                log.warn("Không kiểm tra được replica set MongoDB: {}", ex.getErrorMessage());
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("Không kết nối được MongoDB để kiểm tra replica set: {}", ex.toString());
            return;
        }

        try {
            admin.runCommand(new Document("replSetInitiate", new Document("_id", replSetName)
                    .append("members", List.of(new Document("_id", 0).append("host", host)))));
            waitForPrimary(admin);
            log.info("Đã khởi tạo replica set {} ({})", replSetName, host);
        } catch (RuntimeException ex) {
            log.warn("Không khởi tạo được replica set MongoDB: {}", ex.toString());
        }
    }

    private void waitForPrimary(MongoDatabase admin) {
        for (int i = 0; i < 60; i++) {
            if (Boolean.TRUE.equals(admin.runCommand(new Document("hello", 1)).getBoolean("isWritablePrimary"))) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Replica set đã tạo nhưng node chưa thành PRIMARY sau 30 giây");
    }
}
