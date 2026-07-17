package com.project.cqrs.query.order.model;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_item_query")
@Getter
public class OrderItemQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", referencedColumnName = "order_id", nullable = false)
    private OrderQueryEntity order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    // ── Factory methods ───────────────────────────────────────────────────────

    protected OrderItemQueryEntity() {}

    @Builder
    public static OrderItemQueryEntity of(OrderQueryEntity order, Long productId, String productName, BigDecimal unitPrice, Integer quantity) {
        return OrderItemQueryEntity.builder()
                .order(order)
                .productId(productId)
                .productName(productName)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .build();
    }
}
