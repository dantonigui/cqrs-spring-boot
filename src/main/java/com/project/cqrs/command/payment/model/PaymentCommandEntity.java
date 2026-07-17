package com.project.cqrs.command.payment.model;


import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.shared.enums.PaymentMethod;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.shared.enums.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PaymentCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderCommandEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "mp_payment_id", length = 100)
    private String mpPaymentId;

    @Column(name = "transaction_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal transactionAmount;

    @Column(nullable = false)
    private Integer installments = 1;

    // Presencial
    @Column(name = "in_person_method", length = 30)
    private String inPersonMethod;

    // PIX
    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    @Column(name = "pix_qr_code_base64", columnDefinition = "TEXT")
    private String pixQrCodeBase64;

    @Column(name = "pix_expiration")
    private LocalDateTime pixExpiration;

    // Cartão
    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


    public static PaymentCommandEntity forPix(OrderCommandEntity order) {
        PaymentCommandEntity p = base(order);
        p.paymentMethod = PaymentMethod.PIX;
        p.paymentType = PaymentType.ONLINE;
        return p;
    }

    public static PaymentCommandEntity forInPerson(OrderCommandEntity order, String inPersonMethod) {
        PaymentCommandEntity p = base(order);
        p.paymentType = PaymentType.IN_PERSON;
        p.paymentMethod = PaymentMethod.valueOf(inPersonMethod.toUpperCase());
        p.inPersonMethod = inPersonMethod;
        p.paymentStatus = PaymentStatus.APPROVED;
        return p;
    }

    public static PaymentCommandEntity forCard(OrderCommandEntity order, Integer installments) {
        PaymentCommandEntity p = base(order);
        p.paymentMethod = PaymentMethod.CREDIT_CARD;
        p.paymentType = PaymentType.ONLINE;
        p.installments = installments;
        return p;
    }

    public void approve(String mpPaymentId) {
        this.mpPaymentId = mpPaymentId;
        this.paymentStatus = PaymentStatus.APPROVED;
    }
    public void reject() {
        this.paymentStatus = PaymentStatus.REJECTED;
    }

    private static PaymentCommandEntity base(OrderCommandEntity order) {
        return PaymentCommandEntity.builder()
                .order(order)
                .transactionAmount(order.getTotalAmount())
                .build();
    }

    public void setCardData(String lastFour, String brand) {
        this.cardLastFour = lastFour;
        this.cardBrand    = brand;
    }

    public void setPixData(String qrCode, String qrBase64, LocalDateTime expiration) {
        this.pixQrCode = qrCode;
        this.pixQrCodeBase64 = qrBase64;
        this.pixExpiration = expiration;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void setMpPaymentId(String mpPaymentId) {
        this.mpPaymentId = mpPaymentId;
    }
}
