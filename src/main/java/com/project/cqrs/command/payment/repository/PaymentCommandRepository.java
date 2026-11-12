package com.project.cqrs.command.payment.repository;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PaymentCommandRepository extends JpaRepository<PaymentCommandEntity, Long> {
    @Query("select p from PaymentCommandEntity p where p.order.orderId = :orderId and p.paymentMethod = com.project.cqrs.shared.enums.PaymentMethod.PIX and p.paymentStatus = com.project.cqrs.shared.enums.PaymentStatus.PENDING")
    Optional<PaymentCommandEntity> findPendingPixByOrderId(@Param("orderId") Long orderId);
    Optional<PaymentCommandEntity> findByMpPaymentId(String mpPaymentId);
    List<PaymentCommandEntity> findByOrderOrderId(Long orderId);
}