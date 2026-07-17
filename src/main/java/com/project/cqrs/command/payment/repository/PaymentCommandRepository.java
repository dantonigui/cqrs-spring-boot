package com.project.cqrs.command.payment.repository;

import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentCommandRepository extends JpaRepository<PaymentCommandEntity,Long> {

    Optional<PaymentCommandEntity> findPendingPixByOrderId(Long orderId);

    Optional<PaymentCommandEntity> findByMpPaymentId(String mpPaymentId);
}
