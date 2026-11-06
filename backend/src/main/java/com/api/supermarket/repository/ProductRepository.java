package com.api.supermarket.repository;

import com.api.supermarket.entity.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.productId = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);

  // Lọc sản phẩm đang hoạt động
  List<Product> findByIsActiveTrue();

  // Kiểm tra trùng mã sku
  boolean existsBySkuCode(String skuCode);

  // Lọc sản phẩm theo danh mục
  // Tìm sản phẩm theo mã sku
  Product findBySkuCode(String skuCode);

  // Lấy sản phẩm sắp hết hạn
  // Vì cần so sánh stockQuantity <= reorderPoint nên dùng @Query
  @Query("SELECT p FROM Product p WHERE p.isActive = true AND "
      + "(SELECT COALESCE(SUM(b.remaining), 0) FROM InventoryBatch b "
      + "WHERE b.productId = p.productId AND b.status = 'ACTIVE' "
      + "AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)) <= p.reorderPoint")
  List<Product> findLowStockProducts();

  // Filter products
  @Query("""
      SELECT p FROM Product p
      WHERE (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:categoryName IS NULL OR LOWER(p.categoryName) = LOWER(:categoryName))
        AND (:active IS NULL OR p.isActive = :active)
      """)
  Page<Product> filterProducts(
      @Param("keyword") String keyword,
      @Param("categoryName") String categoryName,
      @Param("active") Boolean active,
      Pageable pageable);
}
