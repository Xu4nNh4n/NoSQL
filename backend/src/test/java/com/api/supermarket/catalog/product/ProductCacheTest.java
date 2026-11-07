package com.api.supermarket.catalog.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.api.supermarket.catalog.product.ProductDtos.PosProduct;
import com.api.supermarket.common.RedisSafe;

import tools.jackson.databind.json.JsonMapper;

class ProductCacheTest {
    private RedisSafe redis;
    private ProductRepository products;
    private ProductCache cache;
    private final JsonMapper json = JsonMapper.builder().build();
    private final PosProduct milk = new PosProduct("p1", "SUA", "Sữa", new BigDecimal("32000"), "c1", "Sữa", null, 5);

    @BeforeEach
    void setUp() {
        redis = mock(RedisSafe.class);
        products = mock(ProductRepository.class);
        cache = new ProductCache(redis, products, json);
        Product p = new Product();
        p.setId("p1");
        p.setSku("SUA");
        p.setName("Sữa");
        p.setPrice(new BigDecimal("32000"));
        p.setCategoryId("c1");
        p.setCategoryName("Sữa");
        p.setStockQuantity(5);
        when(products.findActiveForPos()).thenReturn(List.of(p));
    }

    @Test
    void hitDoesNotTouchMongo() {
        when(redis.get(ProductCache.ACTIVE_KEY)).thenReturn(Optional.of(json.writeValueAsString(List.of(milk))));
        assertEquals(List.of(milk), cache.activeProducts());
        verify(products, never()).findActiveForPos();
    }

    @Test
    void missLoadsMongoAndWritesCacheWithTtl() {
        when(redis.get(ProductCache.ACTIVE_KEY)).thenReturn(Optional.empty());
        assertEquals(List.of(milk), cache.activeProducts());
        verify(redis).set(eq(ProductCache.ACTIVE_KEY), anyString(), eq(ProductCache.ACTIVE_TTL));
    }

    @Test
    void corruptJsonFallsBackToMongo() {
        when(redis.get(ProductCache.ACTIVE_KEY)).thenReturn(Optional.of("{not json"));
        assertEquals(List.of(milk), cache.activeProducts());
    }

    @Test
    void redisDownStillServesData() {
        when(redis.get(anyString())).thenReturn(Optional.empty());
        when(redis.set(anyString(), anyString(), any())).thenReturn(false);
        assertEquals(1, cache.activeProducts().size());
    }

    @Test
    void evictRemovesBothProductCaches() {
        cache.evict();
        verify(redis).delete(ProductCache.ACTIVE_KEY, ProductCache.LOW_STOCK_KEY);
    }
}
