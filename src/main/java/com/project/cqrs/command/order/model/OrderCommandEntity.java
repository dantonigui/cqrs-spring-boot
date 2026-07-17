package com.project.cqrs.command.order.model;

import com.project.cqrs.shared.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@Table(name = "orders")
public class OrderCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "totalAmount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemCommandEntity> items = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    //ver oq significa essa anotação, orphanRemoval e CascadeType
    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    protected OrderCommandEntity() {}

    @Builder
    public static OrderCommandEntity create(Long userId, List<OrderItemCommandEntity> items) {
        OrderCommandEntity order = OrderCommandEntity.builder()
                .userId(userId)
                .items(items)
                .build();

        items.forEach(item -> item.setOrder(order));

        order.setTotalAmount(
                items.stream()
                        .map(item -> item.getUnitPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        return order;
    }

    public void markAsAwaitingPayment() {
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    public void markAsCancelled() {
        this.status = OrderStatus.CANCELLED;
    }

    public void markAsPaid() {
        this.status = OrderStatus.PAID;
    }
}
