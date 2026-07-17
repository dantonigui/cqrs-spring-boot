package com.project.cqrs.command.order.repository;

import com.project.cqrs.command.order.model.OrderCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderCommandRepository extends JpaRepository<OrderCommandEntity, Long> {

    Optional<OrderCommandEntity> findByIdForUpdate(Long orderId);

    boolean hasActivePayment(Long orderId);
}
