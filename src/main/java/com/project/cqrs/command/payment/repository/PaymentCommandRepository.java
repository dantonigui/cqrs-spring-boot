package com.project.cqrs.command.payment.repository;

import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCommandRepository extends JpaRepository<PaymentCommandEntity,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT o
    FROM OrderCommandEntity o
    WHERE o.orderId = :orderId
""")
    Optional<PaymentCommandEntity> findPendingPixByOrderId(@Param("orderId") Long orderId);

    Optional<PaymentCommandEntity> findByMpPaymentId(String mpPaymentId);
}
