package com.project.cqrs.query.payment.model;


import com.project.cqrs.shared.enums.PaymentMethod;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.shared.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payment_query")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PaymentQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "transaction_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal transactionAmount;

    @Column(nullable = false)
    private Integer installments = 1;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "in_person_method", length = 30)
    private String inPersonMethod;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();


    public static PaymentQueryEntity fromApprovedEvent(Long orderId, String mpPaymentId, BigDecimal amount, PaymentMethod paymentMethod) {
        return PaymentQueryEntity.builder()
                .orderId(orderId)
                .mpPaymentId(mpPaymentId)
                .transactionAmount(amount)
                .paymentMethod(paymentMethod)
                .paymentType(PaymentType.ONLINE)
                .paymentStatus(PaymentStatus.APPROVED)
                .installments(1)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
