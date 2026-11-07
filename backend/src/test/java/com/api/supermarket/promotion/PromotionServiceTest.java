package com.api.supermarket.promotion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryRepository;
import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.promotion.PromotionService.PromotionRequest;

class PromotionServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");
    private PromotionRepository repo;
    private PromotionCache cache;
    private PromotionService service;

    @BeforeEach
    void setUp() {
        repo = mock(PromotionRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        cache = mock(PromotionCache.class);
        Product p = new Product();
        p.setId("p1");
        p.setActive(true);
        when(products.findById(anyString())).thenReturn(Optional.empty());
        when(products.findById("p1")).thenReturn(Optional.of(p));
        when(categories.findById("c1")).thenReturn(Optional.of(new Category()));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new PromotionService(repo, products, categories, cache,
                new AppTime(Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"))));
    }

    private static PromotionRequest req(Promotion.Type type, List<String> products, List<String> categories,
            BigDecimal percent, BigDecimal amount, Integer buy, Integer get, BigDecimal minTotal, BigDecimal max) {
        return new PromotionRequest("KM", type, NOW, NOW.plusSeconds(86_400), products, categories, percent, amount, buy,
                get, null, minTotal, max, null, null);
    }

    @Test
    void storesOnlyFieldsOfTheType() {
        Promotion p = service.create(req(Promotion.Type.PERCENT, List.of("p1"), List.of(), new BigDecimal("20"),
                new BigDecimal("999"), 5, 5, null, null));
        assertEquals(new BigDecimal("20"), p.getRule().getPercent());
        assertNull(p.getRule().getAmount(), "trường của loại khác không được lưu");
        assertNull(p.getRule().getBuyQty());
        verify(cache).evict();
    }

    @Test
    void percentAndFixedValidation() {
        assertThrows(BadRequestException.class, () -> service.create(
                req(Promotion.Type.PERCENT, List.of("p1"), List.of(), new BigDecimal("120"), null, null, null, null, null)));
        assertThrows(BadRequestException.class, () -> service.create(
                req(Promotion.Type.PERCENT, List.of(), List.of(), BigDecimal.TEN, null, null, null, null, null)));
        assertThrows(BadRequestException.class, () -> service.create(
                req(Promotion.Type.FIXED, List.of("p1"), List.of(), null, new BigDecimal("1500.5"), null, null, null, null)));
        service.create(req(Promotion.Type.FIXED, List.of(), List.of("c1"), null, new BigDecimal("2000"), null, null, null, null));
    }

    @Test
    void buyXGetYRules() {
        assertThrows(BadRequestException.class, () -> service.create(
                req(Promotion.Type.BUY_X_GET_Y, List.of(), List.of("c1"), null, null, 2, 1, null, null)));
        assertThrows(BadRequestException.class, () -> service.create(
                req(Promotion.Type.BUY_X_GET_Y, List.of("p1"), List.of(), null, null, 2, 0, null, null)));
        PromotionRequest withMissingGift = new PromotionRequest("KM", Promotion.Type.BUY_X_GET_Y, NOW,
                NOW.plusSeconds(60), List.of("p1"), null, null, null, 2, 1, "khongco", null, null, null, null);
        assertThrows(BadRequestException.class, () -> service.create(withMissingGift));
        assertEquals(2, service.create(
                req(Promotion.Type.BUY_X_GET_Y, List.of("p1"), List.of(), null, null, 2, 1, null, null)).getRule().getBuyQty());
    }

    @Test
    void orderThresholdNeedsExactlyOneDiscountKind() {
        assertThrows(BadRequestException.class, () -> service.create(req(Promotion.Type.ORDER_THRESHOLD, null, null,
                BigDecimal.TEN, new BigDecimal("50000"), null, null, new BigDecimal("500000"), null)));
        assertThrows(BadRequestException.class, () -> service.create(req(Promotion.Type.ORDER_THRESHOLD, List.of("p1"),
                null, null, new BigDecimal("50000"), null, null, new BigDecimal("500000"), null)));
        assertThrows(BadRequestException.class, () -> service.create(req(Promotion.Type.ORDER_THRESHOLD, null, null,
                null, new BigDecimal("50000"), null, null, new BigDecimal("500000"), new BigDecimal("1"))));
        Promotion p = service.create(req(Promotion.Type.ORDER_THRESHOLD, null, null, BigDecimal.TEN, null, null, null,
                new BigDecimal("500000"), new BigDecimal("40000")));
        assertEquals(new BigDecimal("40000"), p.getRule().getMaxDiscount());
    }

    @Test
    void endMustBeAfterStartAndVersionMustMatch() {
        assertThrows(BadRequestException.class, () -> service.create(new PromotionRequest("KM", Promotion.Type.PERCENT,
                NOW, NOW, List.of("p1"), null, BigDecimal.TEN, null, null, null, null, null, null, null, null)));
        Promotion existing = new Promotion();
        existing.setVersion(2L);
        when(repo.findById("x")).thenReturn(Optional.of(existing));
        assertThrows(ConflictException.class, () -> service.update("x", new PromotionRequest("KM", Promotion.Type.PERCENT,
                NOW, NOW.plusSeconds(60), List.of("p1"), null, BigDecimal.TEN, null, null, null, null, null, null, null, 1L)));
    }
}
