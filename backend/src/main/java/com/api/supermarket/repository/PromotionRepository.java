package com.api.supermarket.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.api.supermarket.entity.Promotion;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    @Query("select p from Promotion p join fetch p.product order by p.promotionId desc")
    List<Promotion> findAllWithProduct();

    @Query("select p from Promotion p where p.product.productId in :productIds and p.active = true "
            + "and p.startsAt <= :at and p.endsAt > :at order by p.percent desc, p.promotionId asc")
    List<Promotion> findEffective(Collection<Long> productIds, LocalDateTime at);
}
