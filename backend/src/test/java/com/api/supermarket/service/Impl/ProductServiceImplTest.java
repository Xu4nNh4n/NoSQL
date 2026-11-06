package com.api.supermarket.service.Impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.api.supermarket.dto.request.ProductRequest;
import com.api.supermarket.entity.Product;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.integration.DataSyncPublisher;
import com.api.supermarket.repository.*;
import com.api.supermarket.service.InventoryBatchService;

class ProductServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryBatchService inventory = mock(InventoryBatchService.class);
    private final DataSyncPublisher sync = mock(DataSyncPublisher.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products,
            mock(StockMovementRepository.class), inventory, sync);
    private Product product() {
        Product p = new Product(); p.setProductId(1L); p.setSkuCode("1"); p.setProductName("Water");
        p.setPrice(new BigDecimal("5000")); p.setStockQuantity(10); p.setCategoryName("Drinks"); return p;
    }
    @Test void responseUsesCategoryNameAndUsableBatchQuantity() {
        when(products.findById(1L)).thenReturn(Optional.of(product()));
        when(inventory.usableQuantity(1L)).thenReturn(7);
        var response = service.getProductById(1L);
        assertEquals("Drinks", response.getCategoryName());
        assertEquals(7, response.getAvailableQuantity()); assertEquals(10, response.getStockQuantity());
    }
    @Test void editCannotOverwriteBatchStock() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product()));
        ProductRequest r = new ProductRequest(); r.setSkuCode("1"); r.setProductName("Water");
        r.setPrice(new BigDecimal("5000")); r.setStockQuantity(0); r.setCategoryName("Drinks");
        assertThrows(BadRequestException.class, () -> service.updateProduct(1L, r));
        verify(products, never()).save(any()); verifyNoInteractions(sync);
    }
    @Test void hidePreservesHistoryAndPublishesCatalogChange() {
        Product p = product(); when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(p));
        service.deleteProduct(1L);
        assertFalse(p.getIsActive()); verify(products).save(p);
        verify(products, never()).delete(any()); verify(sync).catalog(1L);
    }
}
