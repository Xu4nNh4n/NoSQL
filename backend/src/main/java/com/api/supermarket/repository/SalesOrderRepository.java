package com.api.supermarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.api.supermarket.entity.SalesOrder;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT so FROM SalesOrder so WHERE so.orderId = :id")
        Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);


        Optional<SalesOrder> findByOrderCodeIgnoreCase(String orderCode);

        boolean existsByOrderCodeIgnoreCase(String orderCode);
        @Query("""
                        SELECT so FROM SalesOrder so
                        LEFT JOIN so.user u
                        WHERE (:keyword IS NULL OR LOWER(so.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(so.customerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
                        AND (:status IS NULL OR so.status = :status)
                        AND (:orderType IS NULL OR so.orderType = :orderType)
                        AND (:paymentMethod IS NULL OR so.paymentMethod = :paymentMethod)
                        AND (:customerName IS NULL OR LOWER(so.customerName) LIKE LOWER(CONCAT('%', :customerName, '%')))
                        AND (:staffName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :staffName, '%')))
                        """)
        Page<SalesOrder> filterSalesOrder(
                        @Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("orderType") String orderType,
                        @Param("paymentMethod") String paymentMethod,
                        @Param("customerName") String customerName,
                        @Param("staffName") String staffName,
                        Pageable pageable);
}
