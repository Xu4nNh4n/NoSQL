package com.api.supermarket.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.api.supermarket.entity.Supplier;
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsBySupplierNameIgnoreCaseAndSupplierIdNot(String name, Long id);
}
