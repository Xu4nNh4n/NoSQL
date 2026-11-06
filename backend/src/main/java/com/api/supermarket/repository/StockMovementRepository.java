package com.api.supermarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.api.supermarket.entity.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    org.springframework.data.domain.Page<StockMovement> findByProduct_ProductIdOrderByMovementIdDesc(
            Long productId, org.springframework.data.domain.Pageable pageable);
}
