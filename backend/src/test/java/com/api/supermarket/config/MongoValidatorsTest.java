package com.api.supermarket.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoValidatorsTest {

    @Test
    void schemasCoverMainCollectionsAndGuardStock() {
        Map<String, Document> schemas = MongoValidators.load();
        assertEquals(List.of("users", "products", "batches", "orders", "promotions"), List.copyOf(schemas.keySet()));
        Document stock = schemas.get("products").get("properties", Document.class).get("stockQuantity", Document.class);
        assertEquals(0, stock.getInteger("minimum"), "tồn kho không được âm kể cả khi code có lỗi");
        Document remaining = schemas.get("batches").get("properties", Document.class).get("remainingQty", Document.class);
        assertEquals(0, remaining.getInteger("minimum"));
        assertTrue(schemas.get("orders").getList("required", String.class).contains("requestKey"));
    }
}
