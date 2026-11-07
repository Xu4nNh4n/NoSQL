package com.api.supermarket.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryService;
import com.api.supermarket.catalog.category.CategoryService.CategoryRequest;
import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductDtos.ProductRequest;
import com.api.supermarket.catalog.product.ProductDtos.ProductResponse;
import com.api.supermarket.catalog.product.ProductService;
import com.api.supermarket.support.CloudTest;

/** Text search tiếng Việt và index trên MongoDB Atlas thật (AP-04, AP-05, NFR-12). */
@CloudTest
class CatalogCloudTest {
    @Autowired
    CategoryService categories;
    @Autowired
    ProductService products;
    @Autowired
    MongoTemplate mongo;

    @Test
    void vietnameseTextSearchAndIndexUsage() {
        String tag = String.valueOf(System.nanoTime() % 1_000_000);
        Category c = categories.create(new CategoryRequest("Sữa test " + tag, null, true));
        ProductResponse p = products.create(new ProductRequest("SUA-" + tag, "Sữa tươi Ba Vì " + tag,
                new BigDecimal("32000"), c.getId(), null, 5, null, Map.of("dungTich", "1L"), true, null, null, null));
        try {
            // Không dấu vẫn tìm ra tên có dấu (text index v3, default_language none)
            List<ProductResponse> found = products.list(0, 20, "name", "asc", "sua tuoi " + tag, null, null, null, null)
                    .getContent();
            assertTrue(found.stream().anyMatch(x -> x.id().equals(p.id())), "phải tìm thấy bằng từ khóa không dấu");

            Document plan = mongo.getCollection(Product.COLLECTION)
                    .find(new Document("categoryId", c.getId()).append("price", new Document("$gte", 10000)))
                    .explain();
            String winning = plan.toJson();
            assertTrue(winning.contains("IXSCAN") && winning.contains("categoryId_price"),
                    "lọc theo nhóm + giá phải dùng index categoryId_price: " + winning);

            Product raw = mongo.findById(p.id(), Product.class);
            assertEquals("1L", raw.getAttributes().get("dungTich"));
        } finally {
            mongo.remove(Query.query(Criteria.where("_id").is(p.id())), Product.class);
            mongo.remove(Query.query(Criteria.where("_id").is(c.getId())), Category.class);
        }
    }
}
