package com.project.cqrs.query.order.model;

import com.project.cqrs.query.payment.model.PaymentQueryEntity;
import com.project.cqrs.shared.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_query")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class OrderQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemQueryEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "orderId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentQueryEntity> payments = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Cria a projeção de leitura a partir do evento OrderCreatedEvent.
     * Chamado pelo OrderEventConsumer quando um pedido é criado.
     */

    public static OrderQueryEntity fromCreatedEvent(Long orderId, Long userId, OrderStatus status, BigDecimal totalAmount, LocalDateTime createdAt) {
        return OrderQueryEntity.builder()
                .orderId(orderId)
                .userId(userId)
                .status(status)
                .totalAmount(totalAmount)
                .createdAt(createdAt)
                .build();
    }

    // ── Mutações de status ────────────────────────────────────────────────────

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
