package com.api.supermarket.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.api.supermarket.entity.SalesOrderReturn;

public interface SalesOrderReturnRepository extends JpaRepository<SalesOrderReturn, Long> {
    Optional<SalesOrderReturn> findByRequestKey(String requestKey);
    List<SalesOrderReturn> findByOrderIdOrderByReturnIdAsc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SalesOrderReturn r WHERE r.returnId = :id")
    Optional<SalesOrderReturn> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM SalesOrderReturn r WHERE r.orderId = :orderId "
            + "AND r.refundStatus = 'REFUNDED'")
    java.math.BigDecimal sumRefundedByOrderId(@Param("orderId") Long orderId);

    boolean existsByOrderIdAndRefundStatus(Long orderId, String refundStatus);
    boolean existsByOrderIdAndRefundStatusIn(Long orderId, java.util.Collection<String> refundStatuses);
}
