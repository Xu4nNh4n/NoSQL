package com.api.supermarket.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionTemplate;

import com.api.supermarket.support.CloudTest;

@CloudTest
class MongoTransactionCloudTest {
    @Autowired
    MongoTemplate mongo;
    @Autowired
    MongoTransactionManager txManager;

    record Money(String id, BigDecimal amount) {
    }

    @Test
    void rollbackRemovesEveryWriteInTheTransaction() {
        String marker = "tx-" + System.nanoTime();
        if (!mongo.collectionExists("tx_test")) {
            mongo.createCollection("tx_test"); // tạo collection bên trong transaction không được phép
        }
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            mongo.insert(new Document("marker", marker).append("n", 1), "tx_test");
            mongo.insert(new Document("marker", marker).append("n", 2), "tx_test");
            throw new IllegalStateException("rollback");
        }));
        assertFalse(mongo.exists(Query.query(Criteria.where("marker").is(marker)), "tx_test"));
    }

    @Test
    void bigDecimalIsStoredAsDecimal128() {
        String id = "money-" + System.nanoTime();
        mongo.insert(new Money(id, new BigDecimal("12000")), "tx_test");
        Document raw = mongo.findById(id, Document.class, "tx_test");
        assertEquals(Decimal128.class, raw.get("amount").getClass());
        mongo.remove(Query.query(Criteria.where("_id").is(id)), "tx_test");
    }
}
