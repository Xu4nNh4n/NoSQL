package com.api.supermarket.catalog.category;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.ProductCache;
import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.user.UserFields;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nhóm hàng (FR-03). Đổi tên thì cập nhật tên đã chép trong mọi sản phẩm của nhóm. */
@Service
public class CategoryService {
    public record CategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
            Boolean active) {
    }

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductCache cache;
    private final AppTime time;

    public CategoryService(CategoryRepository categories, ProductRepository products, ProductCache cache, AppTime time) {
        this.categories = categories;
        this.products = products;
        this.cache = cache;
        this.time = time;
    }

    public List<Category> list(Boolean active) {
        return categories.findAll(active);
    }

    public Category load(String id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhóm hàng"));
    }

    public Category create(CategoryRequest req) {
        String name = req.name().trim();
        ensureUniqueName(name, null);
        Category c = new Category();
        c.setName(name);
        c.setNameLower(lower(name));
        c.setDescription(UserFields.text(req.description()));
        c.setActive(req.active() == null || req.active());
        c.setCreatedAt(time.now());
        c.setUpdatedAt(c.getCreatedAt());
        return categories.save(c);
    }

    public Category update(String id, CategoryRequest req) {
        Category c = load(id);
        String name = req.name().trim();
        ensureUniqueName(name, id);
        boolean renamed = !name.equals(c.getName());
        c.setName(name);
        c.setNameLower(lower(name));
        c.setDescription(UserFields.text(req.description()));
        if (req.active() != null) {
            c.setActive(req.active());
        }
        c.setUpdatedAt(time.now());
        Category saved = categories.save(c);
        if (renamed) {
            products.renameCategory(id, name);
            cache.evict();
        }
        return saved;
    }

    public void delete(String id) {
        Category c = load(id);
        if (products.existsByField("categoryId", id)) {
            throw new BadRequestException("Nhóm hàng còn sản phẩm, không xóa được");
        }
        categories.delete(c);
    }

    private void ensureUniqueName(String name, String excludeId) {
        if (categories.existsByNameLower(lower(name), excludeId)) {
            throw new BadRequestException("Tên nhóm hàng đã tồn tại");
        }
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
