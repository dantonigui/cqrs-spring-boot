package com.project.cqrs.query.payment.repository;

import com.project.cqrs.query.payment.model.PaymentQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentQueryRepository extends JpaRepository<PaymentQueryEntity, Long> {

    List<PaymentQueryEntity> findByOrderId(Long orderId);

    boolean existsByOrderIdAndMpPaymentId(Long orderId, String mpPaymentId);
}
