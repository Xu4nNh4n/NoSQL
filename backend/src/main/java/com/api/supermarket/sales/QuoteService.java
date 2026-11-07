package com.api.supermarket.sales;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.Product;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.inventory.Batch;
import com.api.supermarket.inventory.BatchRepository;
import com.api.supermarket.promotion.Pricing.CartItem;
import com.api.supermarket.promotion.Pricing.GiftProduct;
import com.api.supermarket.promotion.Pricing.PricedCart;
import com.api.supermarket.promotion.PromotionCache;
import com.api.supermarket.promotion.PromotionEngine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Tính giá giỏ hàng ở quầy (FR-06 bước 1): giá hiện tại từ MongoDB, khuyến mãi từ cache Redis, quà theo tồn khả dụng. */
@Service
public class QuoteService {
    public record QuoteItem(@NotBlank String productId, @Min(1) @Max(9999) int quantity) {
    }

    public record QuoteRequest(@NotEmpty @Size(max = 100) List<@Valid QuoteItem> items) {
    }

    private final ProductRepository products;
    private final BatchRepository batches;
    private final PromotionCache promotions;
    private final AppTime time;

    public QuoteService(ProductRepository products, BatchRepository batches, PromotionCache promotions, AppTime time) {
        this.products = products;
        this.batches = batches;
        this.promotions = promotions;
        this.time = time;
    }

    public PricedCart quote(List<QuoteItem> items) {
        Map<String, Integer> qty = new LinkedHashMap<>();
        for (QuoteItem i : items) {
            qty.merge(i.productId(), i.quantity(), Integer::sum);
        }
        List<CartItem> cart = qty.entrySet().stream().map(e -> {
            Product p = products.findById(e.getKey())
                    .orElseThrow(() -> new BadRequestException("Sản phẩm không tồn tại: " + e.getKey()));
            if (!p.isActive()) {
                throw new BadRequestException("Sản phẩm " + p.getSku() + " đã ngừng kinh doanh");
            }
            return new CartItem(p.getId(), p.getSku(), p.getName(), p.getCategoryId(), p.getPrice(), e.getValue());
        }).toList();
        return PromotionEngine.price(cart, promotions.active(), this::gift, time.now());
    }

    private Optional<GiftProduct> gift(String productId) {
        return products.findById(productId).filter(Product::isActive).map(p -> new GiftProduct(p.getId(), p.getSku(),
                p.getName(), p.getCategoryId(), p.getPrice(), available(p.getId())));
    }

    int available(String productId) {
        return batches.findAvailableForFefo(productId, time.today()).stream().mapToInt(Batch::getRemainingQty).sum();
    }
}
