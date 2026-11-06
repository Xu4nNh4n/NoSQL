package com.api.supermarket.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.api.supermarket.entity.InventoryBatch;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBatch b where b.productId = :productId order by b.id")
    List<InventoryBatch> lockProductBatches(@Param("productId") Long productId);
    List<InventoryBatch> findByProductIdOrderByIdAsc(Long productId);
    List<InventoryBatch> findAllByOrderByIdAsc();
    Optional<InventoryBatch> findByCode(String code);
    @Query(value = "select coalesce(max(cast(batch_code as unsigned)), 0) + 1 from inventory_batches "
            + "where batch_code regexp '^[0-9]+$'", nativeQuery = true)
    long nextSimpleCodeValue();
    @Query("select coalesce(sum(b.remaining), 0) from InventoryBatch b "
            + "where b.productId = :productId and b.status = 'ACTIVE' "
            + "and (b.expiryDate is null or b.expiryDate >= :today)")
    Long usableQuantity(@Param("productId") Long productId, @Param("today") java.time.LocalDate today);
}
