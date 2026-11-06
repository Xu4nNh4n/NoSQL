package com.api.supermarket.controller;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.api.supermarket.entity.Supplier;
import com.api.supermarket.repository.SupplierRepository;
import com.api.supermarket.exception.*;

@RestController
@RequestMapping("/api/suppliers")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','WAREHOUSE')")
public class SupplierController {
    private final SupplierRepository suppliers;
    public SupplierController(SupplierRepository suppliers) { this.suppliers = suppliers; }
    public record Request(@NotBlank @Size(max=150) String supplierName, @Size(max=20) String phone,
            @Size(max=255) String address, @Size(max=150) String contactName) {}
    @GetMapping
    public List<Supplier> list() {
        return suppliers.findAll(org.springframework.data.domain.Sort.by("supplierName"));
    }
    @PostMapping @Transactional
    public Supplier create(@Valid @RequestBody Request request) { return save(new Supplier(), request); }
    @PutMapping("/{id}") @Transactional
    public Supplier update(@PathVariable Long id, @Valid @RequestBody Request request) {
        return save(suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp")), request);
    }
    private Supplier save(Supplier supplier, Request request) {
        String name = request.supplierName().trim();
        if (suppliers.existsBySupplierNameIgnoreCaseAndSupplierIdNot(name, supplier.supplierId == null ? -1L : supplier.supplierId)) {
            throw new BadRequestException("Tên nhà cung cấp đã tồn tại");
        }
        supplier.supplierName = name;
        supplier.contactName = request.contactName() == null || request.contactName().isBlank() ? null : request.contactName().trim();
        supplier.phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        supplier.address = request.address() == null || request.address().isBlank() ? null : request.address().trim();
        return suppliers.save(supplier);
    }

    @DeleteMapping("/{id}") @Transactional
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void disable(@PathVariable Long id) { setActive(id, false); }

    @PutMapping("/{id}/restore") @Transactional
    public Supplier restore(@PathVariable Long id) { return setActive(id, true); }

    private Supplier setActive(Long id, boolean active) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp"));
        supplier.isActive = active;
        return suppliers.save(supplier);
    }
}
