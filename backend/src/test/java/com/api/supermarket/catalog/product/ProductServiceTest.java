package com.api.supermarket.catalog.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryRepository;
import com.api.supermarket.catalog.product.ProductDtos.ProductRequest;
import com.api.supermarket.catalog.product.ProductDtos.ProductResponse;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.exception.BadRequestException;

class ProductServiceTest {
    private ProductRepository products;
    private CategoryRepository categories;
    private SupplierService suppliers;
    private ProductCache cache;
    private ProductService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        categories = mock(CategoryRepository.class);
        suppliers = mock(SupplierService.class);
        cache = mock(ProductCache.class);
        AppTime time = new AppTime(Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        service = new ProductService(products, categories, suppliers, cache, time);

        Category milk = new Category();
        milk.setId("c1");
        milk.setName("Sữa");
        milk.setActive(true);
        when(categories.findById("c1")).thenReturn(Optional.of(milk));
        Category old = new Category();
        old.setId("c2");
        old.setName("Cũ");
        old.setActive(false);
        when(categories.findById("c2")).thenReturn(Optional.of(old));
        when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProductRequest req(String sku, String categoryId, Long version, Integer stock) {
        return new ProductRequest(sku, "Sữa tươi 1L", new BigDecimal("32000"), categoryId, null, 20, null,
                Map.of("dungTich", "1L"), null, version, stock, null);
    }

    @Test
    void createNormalizesSkuCopiesCategoryNameAndEvictsCache() {
        ProductResponse res = service.create(req(" sua-tt-1l ", "c1", null, null));
        assertEquals("SUA-TT-1L", res.sku());
        assertEquals("Sữa", res.categoryName());
        assertEquals(0, res.stockQuantity());
        assertEquals(Map.of("dungTich", "1L"), res.attributes());
        verify(cache).evict();
    }

    @Test
    void duplicateSkuIsRejected() {
        when(products.existsBySku("SUA-TT-1L", null)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create(req("sua-tt-1l", "c1", null, null)));
    }

    @Test
    void inactiveCategoryIsRejectedForNewProducts() {
        assertThrows(BadRequestException.class, () -> service.create(req("A1", "c2", null, null)));
    }

    @Test
    void stockCannotBeEditedDirectly() {
        assertThrows(BadRequestException.class, () -> service.create(req("A1", "c1", null, 5)));
        verify(products, never()).save(any());
    }

    @Test
    void staleVersionIsConflict() {
        Product p = new Product();
        p.setId("p1");
        p.setSku("A1");
        p.setVersion(3L);
        when(products.findById("p1")).thenReturn(Optional.of(p));
        assertThrows(ConflictException.class, () -> service.update("p1", req("A1", "c1", 2L, null)));
        assertThrows(BadRequestException.class, () -> service.update("p1", req("A1", "c1", null, null)));
    }

    @Test
    void updateKeepsStockAndChecksSkuOnlyWhenChanged() {
        Product p = new Product();
        p.setId("p1");
        p.setSku("A1");
        p.setVersion(3L);
        p.setStockQuantity(40);
        when(products.findById("p1")).thenReturn(Optional.of(p));
        ProductResponse res = service.update("p1", req("a1", "c1", 3L, null));
        assertEquals(40, res.stockQuantity());
        verify(products, never()).existsBySku(anyString(), anyString());
    }

    @Test
    void deactivateIsSoftDelete() {
        Product p = new Product();
        p.setId("p1");
        p.setActive(true);
        when(products.findById("p1")).thenReturn(Optional.of(p));
        service.deactivate("p1");
        assertFalse(p.isActive());
        verify(cache).evict();
    }

    @Test
    void priceRangeMustBeOrdered() {
        assertThrows(BadRequestException.class, () -> service.list(0, 20, "name", "asc", null, null,
                new BigDecimal("50000"), new BigDecimal("10000"), null));
    }
}
