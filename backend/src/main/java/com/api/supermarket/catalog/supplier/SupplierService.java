package com.api.supermarket.catalog.supplier;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.api.supermarket.catalog.product.ProductRepository;
import com.api.supermarket.common.AppTime;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.user.UserFields;
import com.api.supermarket.validation.PaginationValidator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nhà cung cấp (FR-03). Ngừng hoạt động thì không nhận phiếu nhập hoặc lô mới. */
@Service
public class SupplierService {
    public static final Set<String> SORT_FIELDS = Set.of("name", "createdAt");

    public record SupplierRequest(
            @NotBlank @Size(max = 200) String name,
            @Pattern(regexp = "^(0\\d{8,10})?$", message = "Số điện thoại 9–11 số, bắt đầu bằng 0") String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 255) String address,
            Boolean active) {
    }

    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final AppTime time;

    public SupplierService(SupplierRepository suppliers, ProductRepository products, AppTime time) {
        this.suppliers = suppliers;
        this.products = products;
        this.time = time;
    }

    public PageResponse<Supplier> list(int page, int size, String sortBy, String sortDir, String keyword, Boolean active) {
        return PageResponse.of(suppliers.search(keyword, active,
                PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS)));
    }

    public Supplier load(String id) {
        return suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp"));
    }

    /** Dùng khi nhập hàng và tạo sản phẩm: nhà cung cấp phải tồn tại và đang hoạt động. */
    public Supplier loadActive(String id) {
        Supplier s = load(id);
        if (!s.isActive()) {
            throw new BadRequestException("Nhà cung cấp đã ngừng hoạt động");
        }
        return s;
    }

    public Supplier create(SupplierRequest req) {
        Supplier s = new Supplier();
        apply(s, req, null);
        s.setActive(req.active() == null || req.active());
        s.setCreatedAt(time.now());
        s.setUpdatedAt(s.getCreatedAt());
        return suppliers.save(s);
    }

    public Supplier update(String id, SupplierRequest req) {
        Supplier s = load(id);
        apply(s, req, id);
        if (req.active() != null) {
            s.setActive(req.active());
        }
        s.setUpdatedAt(time.now());
        return suppliers.save(s);
    }

    public void delete(String id) {
        Supplier s = load(id);
        if (products.existsByField("supplierId", id) || suppliers.hasBatches(id)) {
            throw new BadRequestException("Nhà cung cấp còn sản phẩm hoặc lô hàng; hãy chuyển sang ngừng hoạt động");
        }
        suppliers.delete(s);
    }

    private void apply(Supplier s, SupplierRequest req, String excludeId) {
        String phone = UserFields.phone(req.phone());
        String email = UserFields.email(req.email());
        if (phone != null && suppliers.existsByField("phone", phone, excludeId)) {
            throw new BadRequestException("Số điện thoại nhà cung cấp đã tồn tại");
        }
        if (email != null && suppliers.existsByField("email", email, excludeId)) {
            throw new BadRequestException("Email nhà cung cấp đã tồn tại");
        }
        s.setName(req.name().trim());
        s.setPhone(phone);
        s.setEmail(email);
        s.setAddress(UserFields.text(req.address()));
    }
}
