package com.api.supermarket.catalog.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.category.Category;
import com.api.supermarket.catalog.category.CategoryRepository;
import com.api.supermarket.catalog.product.ProductDtos.PosProduct;
import com.api.supermarket.catalog.product.ProductDtos.ProductRequest;
import com.api.supermarket.catalog.product.ProductDtos.ProductResponse;
import com.api.supermarket.catalog.supplier.SupplierService;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.user.UserFields;
import com.api.supermarket.validation.PaginationValidator;

/** Sản phẩm (FR-04). Tồn kho không sửa ở đây; chỉ lô hàng, bán và trả được đổi tồn. */
@Service
public class ProductService {
    public static final Set<String> SORT_FIELDS = Set.of("name", "sku", "price", "stockQuantity", "createdAt");

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierService suppliers;
    private final ProductCache cache;
    private final AppTime time;

    public ProductService(ProductRepository products, CategoryRepository categories, SupplierService suppliers,
            ProductCache cache, AppTime time) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.cache = cache;
        this.time = time;
    }

    public PageResponse<ProductResponse> list(int page, int size, String sortBy, String sortDir, String keyword,
            String categoryId, BigDecimal minPrice, BigDecimal maxPrice, Boolean active) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("Giá từ phải nhỏ hơn hoặc bằng giá đến");
        }
        var pageable = PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS);
        return PageResponse.of(products.search(keyword, categoryId, minPrice, maxPrice, active, pageable)
                .map(ProductResponse::from));
    }

    public Product load(String id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
    }

    public ProductResponse get(String id) {
        return ProductResponse.from(load(id));
    }

    public ProductResponse getBySku(String sku) {
        return products.findBySku(normalizeSku(sku)).map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm có SKU " + sku));
    }

    public List<PosProduct> activeForPos() {
        return cache.activeProducts();
    }

    public ProductResponse create(ProductRequest req) {
        rejectStockFields(req);
        String sku = normalizeSku(req.sku());
        if (products.existsBySku(sku, null)) {
            throw new BadRequestException("SKU đã tồn tại");
        }
        Product p = new Product();
        p.setSku(sku);
        apply(p, req);
        p.setActive(req.active() == null || req.active());
        p.setCreatedAt(time.now());
        p.setUpdatedAt(p.getCreatedAt());
        Product saved = products.save(p);
        cache.evict();
        return ProductResponse.from(saved);
    }

    public ProductResponse update(String id, ProductRequest req) {
        rejectStockFields(req);
        if (req.version() == null) {
            throw new BadRequestException("Thiếu version của sản phẩm");
        }
        Product p = load(id);
        if (!Objects.equals(p.getVersion(), req.version())) {
            throw new ConflictException("STALE_VERSION", "Sản phẩm vừa được người khác cập nhật, vui lòng tải lại");
        }
        String sku = normalizeSku(req.sku());
        if (!sku.equals(p.getSku()) && products.existsBySku(sku, id)) {
            throw new BadRequestException("SKU đã tồn tại");
        }
        p.setSku(sku);
        apply(p, req);
        if (req.active() != null) {
            p.setActive(req.active());
        }
        p.setUpdatedAt(time.now());
        Product saved = products.save(p); // @Version chặn ghi đè nếu có người lưu chen giữa
        cache.evict();
        return ProductResponse.from(saved);
    }

    /** Ngừng kinh doanh (xóa mềm): giữ lại để đơn cũ và thẻ kho còn tham chiếu được. */
    public void deactivate(String id) {
        Product p = load(id);
        if (!p.isActive()) {
            return;
        }
        p.setActive(false);
        p.setUpdatedAt(time.now());
        products.save(p);
        cache.evict();
    }

    private void apply(Product p, ProductRequest req) {
        Category category = categories.findById(req.categoryId())
                .orElseThrow(() -> new BadRequestException("Nhóm hàng không tồn tại"));
        if (!category.isActive() && !category.getId().equals(p.getCategoryId())) {
            throw new BadRequestException("Nhóm hàng đã ngừng hoạt động");
        }
        String supplierId = UserFields.text(req.supplierId());
        if (supplierId != null && !supplierId.equals(p.getSupplierId())) {
            suppliers.loadActive(supplierId);
        }
        p.setName(req.name().trim());
        p.setPrice(req.price());
        p.setCategoryId(category.getId());
        p.setCategoryName(category.getName());
        p.setSupplierId(supplierId);
        p.setReorderPoint(req.reorderPoint() == null ? 0 : req.reorderPoint());
        p.setImageUrl(UserFields.text(req.imageUrl()));
        p.setAttributes(ProductAttributes.validate(req.attributes()));
    }

    private static void rejectStockFields(ProductRequest req) {
        if (req.stockQuantity() != null || req.damagedQuantity() != null) {
            throw new BadRequestException("Tồn kho chỉ thay đổi qua nhập lô, bán và trả hàng");
        }
    }

    static String normalizeSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
    }
}
