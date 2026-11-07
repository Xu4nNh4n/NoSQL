package com.api.supermarket.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.category.CategoryRepository;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.validation.PaginationValidator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Quản lý khuyến mãi (FR-11). Mỗi loại kiểm tra đúng các tham số của nó. */
@Service
public class PromotionService {
    public static final Set<String> SORT_FIELDS = Set.of("startsAt", "endsAt", "name", "createdAt");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record PromotionRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull Promotion.Type type,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            List<String> productIds,
            List<String> categoryIds,
            BigDecimal percent,
            BigDecimal amount,
            Integer buyQty,
            Integer getQty,
            String giftProductId,
            BigDecimal minTotal,
            BigDecimal maxDiscount,
            Boolean active,
            Long version) {
    }

    private final PromotionRepository promotions;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final PromotionCache cache;
    private final AppTime time;

    public PromotionService(PromotionRepository promotions, ProductRepository products, CategoryRepository categories,
            PromotionCache cache, AppTime time) {
        this.promotions = promotions;
        this.products = products;
        this.categories = categories;
        this.cache = cache;
        this.time = time;
    }

    public PageResponse<Promotion> list(Promotion.Type type, Boolean active, int page, int size, String sortBy,
            String sortDir) {
        return PageResponse.of(promotions.search(type, active,
                PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS)));
    }

    public Promotion load(String id) {
        return promotions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khuyến mãi"));
    }

    public List<Promotion> active() {
        return cache.active();
    }

    public Promotion create(PromotionRequest req) {
        Promotion p = new Promotion();
        apply(p, req);
        p.setActive(req.active() == null || req.active());
        p.setCreatedAt(time.now());
        p.setUpdatedAt(p.getCreatedAt());
        Promotion saved = promotions.save(p);
        cache.evict();
        return saved;
    }

    public Promotion update(String id, PromotionRequest req) {
        Promotion p = load(id);
        if (req.version() == null || !Objects.equals(req.version(), p.getVersion())) {
            throw new ConflictException("STALE_VERSION", "Khuyến mãi vừa được người khác cập nhật, vui lòng tải lại");
        }
        apply(p, req);
        if (req.active() != null) {
            p.setActive(req.active());
        }
        p.setUpdatedAt(time.now());
        Promotion saved = promotions.save(p);
        cache.evict();
        return saved;
    }

    public Promotion setActive(String id, boolean active) {
        Promotion p = load(id);
        p.setActive(active);
        p.setUpdatedAt(time.now());
        Promotion saved = promotions.save(p);
        cache.evict();
        return saved;
    }

    private void apply(Promotion p, PromotionRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BadRequestException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        List<String> productIds = distinct(req.productIds());
        List<String> categoryIds = distinct(req.categoryIds());
        Promotion.Rule rule = new Promotion.Rule();
        switch (req.type()) {
            case PERCENT -> {
                requireScope(productIds, categoryIds);
                rule.setPercent(percent(req.percent()));
            }
            case FIXED -> {
                requireScope(productIds, categoryIds);
                rule.setAmount(positiveMoney(req.amount(), "Số tiền giảm"));
            }
            case BUY_X_GET_Y -> {
                if (productIds.isEmpty() || !categoryIds.isEmpty()) {
                    throw new BadRequestException("Mua X tặng Y chỉ áp theo danh sách sản phẩm");
                }
                rule.setBuyQty(positive(req.buyQty(), "Số lượng mua"));
                rule.setGetQty(positive(req.getQty(), "Số lượng tặng"));
                if (req.giftProductId() != null && !req.giftProductId().isBlank()) {
                    if (!products.findById(req.giftProductId()).map(x -> x.isActive()).orElse(false)) {
                        throw new BadRequestException("Sản phẩm quà không tồn tại hoặc đã ngừng kinh doanh");
                    }
                    rule.setGiftProductId(req.giftProductId());
                }
            }
            case ORDER_THRESHOLD -> {
                if (!productIds.isEmpty() || !categoryIds.isEmpty()) {
                    throw new BadRequestException("Khuyến mãi theo tổng đơn áp cho cả đơn, không chọn sản phẩm");
                }
                rule.setMinTotal(positiveMoney(req.minTotal(), "Tổng đơn tối thiểu"));
                if ((req.amount() == null) == (req.percent() == null)) {
                    throw new BadRequestException("Chọn đúng một cách giảm: số tiền hoặc phần trăm");
                }
                if (req.amount() != null) {
                    rule.setAmount(positiveMoney(req.amount(), "Số tiền giảm"));
                    if (req.maxDiscount() != null) {
                        throw new BadRequestException("Mức giảm tối đa chỉ dùng với giảm theo phần trăm");
                    }
                } else {
                    rule.setPercent(percent(req.percent()));
                    if (req.maxDiscount() != null) {
                        rule.setMaxDiscount(positiveMoney(req.maxDiscount(), "Mức giảm tối đa"));
                    }
                }
            }
        }
        for (String id : productIds) {
            if (products.findById(id).isEmpty()) {
                throw new BadRequestException("Sản phẩm không tồn tại: " + id);
            }
        }
        for (String id : categoryIds) {
            if (categories.findById(id).isEmpty()) {
                throw new BadRequestException("Nhóm hàng không tồn tại: " + id);
            }
        }
        p.setName(req.name().trim());
        p.setType(req.type());
        p.setStartsAt(req.startsAt());
        p.setEndsAt(req.endsAt());
        Promotion.Scope scope = new Promotion.Scope();
        scope.setProductIds(productIds);
        scope.setCategoryIds(categoryIds);
        p.setScope(scope);
        p.setRule(rule);
    }

    private static List<String> distinct(List<String> ids) {
        return ids == null ? new ArrayList<>()
                : new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(s -> s != null && !s.isBlank()).toList()));
    }

    private static void requireScope(List<String> productIds, List<String> categoryIds) {
        if (productIds.isEmpty() && categoryIds.isEmpty()) {
            throw new BadRequestException("Chọn ít nhất một sản phẩm hoặc nhóm hàng");
        }
    }

    private static BigDecimal percent(BigDecimal v) {
        if (v == null || v.signum() <= 0 || v.compareTo(HUNDRED) > 0 || v.scale() > 2) {
            throw new BadRequestException("Phần trăm giảm phải trong khoảng (0, 100], tối đa 2 chữ số thập phân");
        }
        return v;
    }

    private static BigDecimal positiveMoney(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0 || v.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException(label + " phải là số tiền VND dương, không có phần lẻ");
        }
        return v;
    }

    private static int positive(Integer v, String label) {
        if (v == null || v <= 0) {
            throw new BadRequestException(label + " phải lớn hơn 0");
        }
        return v;
    }
}
