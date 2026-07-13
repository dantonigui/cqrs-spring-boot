package com.project.cqrs.command.payment.repository;

import com.project.cqrs.command.payment.model.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity,Long> {

    Optional<PaymentEntity> findPendingPixByOrderId(Long orderId);

    Optional<PaymentEntity> findByMpPaymentId(String mpPaymentId);
}
