package com.api.supermarket.customer;

import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.api.supermarket.common.AppTime;
import com.api.supermarket.common.ConflictException;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.user.UserFields;
import com.api.supermarket.validation.PaginationValidator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Khách hàng (FR-03): SĐT duy nhất, email duy nhất không phân biệt hoa thường. */
@Service
public class CustomerService {
    public static final Set<String> SORT_FIELDS = Set.of("fullName", "phone", "createdAt");

    public record CustomerRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = "^0\\d{9,10}$", message = "Số điện thoại 10–11 số, bắt đầu bằng 0") String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 255) String address,
            Boolean active,
            Long version) {
    }

    private final CustomerRepository customers;
    private final AppTime time;

    public CustomerService(CustomerRepository customers, AppTime time) {
        this.customers = customers;
        this.time = time;
    }

    public PageResponse<Customer> list(int page, int size, String sortBy, String sortDir, String keyword, Boolean active) {
        return PageResponse.of(customers.search(keyword, active,
                PaginationValidator.pageRequest(page, size, sortBy, sortDir, SORT_FIELDS)));
    }

    public Customer load(String id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));
    }

    public Customer byPhone(String phone) {
        return customers.findByPhone(UserFields.phone(phone))
                .orElseThrow(() -> new ResourceNotFoundException("Không có khách hàng với số điện thoại này"));
    }

    /** Khi bán: khách phải còn hoạt động mới được gắn vào đơn mới. */
    public Customer loadActive(String id) {
        Customer c = load(id);
        if (!c.isActive()) {
            throw new BadRequestException("Khách hàng đã ngừng hoạt động");
        }
        return c;
    }

    public Customer create(CustomerRequest req) {
        Customer c = new Customer();
        apply(c, req, null);
        c.setActive(true);
        c.setCreatedAt(time.now());
        c.setUpdatedAt(c.getCreatedAt());
        return customers.save(c);
    }

    public Customer update(String id, CustomerRequest req, boolean canChangeStatus) {
        Customer c = load(id);
        if (req.version() == null || !Objects.equals(req.version(), c.getVersion())) {
            throw new ConflictException("STALE_VERSION", "Khách hàng vừa được người khác cập nhật, vui lòng tải lại");
        }
        apply(c, req, id);
        if (canChangeStatus && req.active() != null) {
            c.setActive(req.active());
        }
        c.setUpdatedAt(time.now());
        return customers.save(c);
    }

    public void deactivate(String id) {
        Customer c = load(id);
        if (c.isActive()) {
            c.setActive(false);
            c.setUpdatedAt(time.now());
            customers.save(c);
        }
    }

    private void apply(Customer c, CustomerRequest req, String excludeId) {
        String phone = UserFields.phone(req.phone());
        String email = UserFields.email(req.email());
        if (customers.existsByField("phone", phone, excludeId)) {
            throw new BadRequestException("Số điện thoại khách hàng đã tồn tại");
        }
        if (email != null && customers.existsByField("email", email, excludeId)) {
            throw new BadRequestException("Email khách hàng đã tồn tại");
        }
        c.setFullName(req.fullName().trim());
        c.setPhone(phone);
        c.setEmail(email);
        c.setAddress(UserFields.text(req.address()));
    }
}
