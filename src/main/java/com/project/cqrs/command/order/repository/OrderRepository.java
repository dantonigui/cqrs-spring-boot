package com.project.cqrs.command.order.repository;

import com.project.cqrs.command.order.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByIdForUpdate(Long orderId);

    boolean hasActivePayment(Long orderId);
}
