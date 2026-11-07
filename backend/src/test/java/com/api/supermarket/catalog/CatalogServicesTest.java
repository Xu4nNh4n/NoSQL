package com.api.supermarket.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryRepository;
import com.api.supermarket.catalog.category.CategoryService;
import com.api.supermarket.catalog.category.CategoryService.CategoryRequest;
import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.catalog.supplier.Supplier;
import com.api.supermarket.catalog.supplier.SupplierRepository;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.catalog.supplier.SupplierService.SupplierRequest;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.exception.BadRequestException;

/** Nhóm hàng, nhà cung cấp, khách hàng: các quy tắc trùng lặp và xóa. */
class CatalogServicesTest {
    private final AppTime time = new AppTime(Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
    private ProductRepository products;
    private ProductCache cache;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        cache = mock(ProductCache.class);
    }

    @Test
    void categoryNameIsUniqueIgnoringCase() {
        CategoryRepository repo = mock(CategoryRepository.class);
        when(repo.existsByNameLower("sữa", null)).thenReturn(true);
        CategoryService service = new CategoryService(repo, products, cache, time);
        assertThrows(BadRequestException.class, () -> service.create(new CategoryRequest(" Sữa ", null, null)));
    }

    @Test
    void renamingCategoryUpdatesProductsAndCache() {
        CategoryRepository repo = mock(CategoryRepository.class);
        Category c = new Category();
        c.setId("c1");
        c.setName("Sữa");
        when(repo.findById("c1")).thenReturn(Optional.of(c));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CategoryService service = new CategoryService(repo, products, cache, time);

        service.update("c1", new CategoryRequest("Sữa & chế phẩm", null, null));
        verify(products).renameCategory("c1", "Sữa & chế phẩm");
        verify(cache).evict();

        service.update("c1", new CategoryRequest("Sữa & chế phẩm", "mô tả mới", null));
        verify(products).renameCategory(anyString(), anyString()); // vẫn chỉ 1 lần: tên không đổi
    }

    @Test
    void categoryWithProductsCannotBeDeleted() {
        CategoryRepository repo = mock(CategoryRepository.class);
        Category c = new Category();
        c.setId("c1");
        when(repo.findById("c1")).thenReturn(Optional.of(c));
        when(products.existsByField("categoryId", "c1")).thenReturn(true);
        CategoryService service = new CategoryService(repo, products, cache, time);
        assertThrows(BadRequestException.class, () -> service.delete("c1"));
        verify(repo, never()).delete(any());
    }

    @Test
    void supplierInUseCannotBeDeleted() {
        SupplierRepository repo = mock(SupplierRepository.class);
        Supplier s = new Supplier();
        s.setId("s1");
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        when(repo.hasBatches("s1")).thenReturn(true);
        SupplierService service = new SupplierService(repo, products, time);
        assertThrows(BadRequestException.class, () -> service.delete("s1"));
        verify(repo, never()).delete(any());
    }

    @Test
    void inactiveSupplierCannotReceiveGoods() {
        SupplierRepository repo = mock(SupplierRepository.class);
        Supplier s = new Supplier();
        s.setId("s1");
        s.setActive(false);
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        assertThrows(BadRequestException.class, () -> new SupplierService(repo, products, time).loadActive("s1"));
    }

    @Test
    void supplierDuplicatePhoneRejected() {
        SupplierRepository repo = mock(SupplierRepository.class);
        when(repo.existsByField("phone", "0281234567", null)).thenReturn(true);
        SupplierService service = new SupplierService(repo, products, time);
        assertThrows(BadRequestException.class,
                () -> service.create(new SupplierRequest("Vinamilk", "0281234567", null, null, null)));
    }
}
