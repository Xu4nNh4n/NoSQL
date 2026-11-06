package com.api.supermarket.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.api.supermarket.entity.PaymentRecord;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    Optional<PaymentRecord> findFirstByOrderIdAndEventTypeOrderByPaymentRecordIdDesc(Long orderId, String eventType);
    Optional<PaymentRecord> findByReturnIdAndEventType(Long returnId, String eventType);
    boolean existsByReference(String reference);
    java.util.List<PaymentRecord> findByOrderIdOrderByPaymentRecordIdAsc(Long orderId);
}
