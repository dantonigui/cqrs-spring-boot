package com.project.cqrs.command.order.repository;

import com.project.cqrs.command.order.model.OrderCommandEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderCommandRepository extends JpaRepository<OrderCommandEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT o
    FROM OrderCommandEntity o
    WHERE o.orderId = :orderId
""")
    Optional<OrderCommandEntity> findByIdForUpdate(@Param("orderId") Long orderId);

    @Query("select (count(p) > 0) from PaymentCommandEntity p where p.order.orderId = :orderId and p.paymentStatus in (com.project.cqrs.shared.enums.PaymentStatus.PENDING, com.project.cqrs.shared.enums.PaymentStatus.APPROVED, com.project.cqrs.shared.enums.PaymentStatus.IN_PROCESS)")
    boolean hasActivePayment(@Param("orderId") Long orderId);
    @Query("select o from OrderCommandEntity o where o.createdAt < :before and o.status in (com.project.cqrs.shared.enums.OrderStatus.PENDING, com.project.cqrs.shared.enums.OrderStatus.AWAITING_PAYMENT) order by o.orderId")
    java.util.List<OrderCommandEntity> expired(@Param("before") java.time.LocalDateTime before, org.springframework.data.domain.Pageable pageable);
}
