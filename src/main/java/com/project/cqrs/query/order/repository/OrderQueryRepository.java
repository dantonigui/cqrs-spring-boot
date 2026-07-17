package com.project.cqrs.query.order.repository;

import com.project.cqrs.query.order.model.OrderQueryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderQueryRepository extends JpaRepository<OrderQueryEntity, Long> {

    /**
     * Busca um pedido pelo orderId (chave de negócio).
     * Carrega itens e pagamentos em uma query só (JOIN FETCH)
     * para evitar N+1 ao montar o DTO de detalhe.
     */
    @Query("""
        SELECT o FROM OrderQueryEntity o
        LEFT JOIN FETCH o.items
        LEFT JOIN FETCH o.payments
        WHERE o.orderId = :orderId
    """)
    Optional<OrderQueryEntity> findByOrderId(@Param("orderId") Long orderId);

    Page<OrderQueryEntity> findByUserIdOrderByCreatedAtDesc(
            Long userId, Pageable pageable);

    boolean existsByOrderId(Long orderId);
}
