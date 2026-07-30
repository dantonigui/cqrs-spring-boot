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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT o
    FROM OrderCommandEntity o
    WHERE o.orderId = :orderId
""")
    boolean hasActivePayment(@Param("orderId") Long orderId);
}
