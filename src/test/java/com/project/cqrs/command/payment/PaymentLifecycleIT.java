package com.project.cqrs.command.payment;

import com.project.cqrs.AbstractIntegrationTest;
import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import com.project.cqrs.command.product.repository.ProductRepository;
import com.project.cqrs.command.order.dto.*;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.order.service.*;
import com.project.cqrs.command.payment.dto.request.InPersonCheckoutRequestDTO;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.command.payment.service.MercadoPagoPaymentService;
import com.project.cqrs.command.webhook.service.MercadoPagoWebhookProcessor;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.payment.repository.PaymentQueryRepository;
import com.project.cqrs.shared.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentLifecycleIT extends AbstractIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired OrderCommandRepository commandOrders;
    @Autowired OrderCommandService orders;
    @Autowired OrderCancelledService cancellations;
    @Autowired PaymentCommandRepository payments;
    @Autowired MercadoPagoPaymentService checkout;
    @Autowired MercadoPagoWebhookProcessor webhook;
    @Autowired OrderQueryRepository queryOrders;
    @Autowired PaymentQueryRepository queryPayments;
    @Autowired com.project.cqrs.command.product.kafka.producer.ProductEventProducer productEvents;

    private long createOrder() {
        var category = categories.save(CategoryCommandEntity.createCategory("Pagamento"));
        var product = ProductCommandEntity.createProduct("Produto", UUID.randomUUID().toString(), new BigDecimal("10.00"), "https://example.com/image.png", category);
        product.replenishStock(2);
        product = products.save(product);
        productEvents.sendProductCreated(product.getProductId().toString(), com.project.cqrs.shared.event.product.ProductCreateEvent.fromEntity(product));
        return orders.createOrder(42L, new CreateOrderRequestDTO(List.of(new OrderItemDTO(product.getProductId(), 1))));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void inPersonApprovalAndCancellationReachProjection() {
        long id = createOrder();
        checkout.registerInPersonPayment(id, new InPersonCheckoutRequestDTO("CASH"));
        org.awaitility.Awaitility.await().atMost(40, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(queryOrders.findByOrderId(id)).isPresent();
            assertThat(queryOrders.findByOrderId(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(queryPayments.findByOrderId(id)).hasSize(1);
            assertThat(queryPayments.findByOrderId(id).getFirst().getPaymentType()).isEqualTo(PaymentType.IN_PERSON);
        });
        cancellations.cancelOrder(id, 42L, "Devolução autorizada");
        org.awaitility.Awaitility.await().atMost(40, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(queryOrders.findByOrderId(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(queryPayments.findByOrderId(id).getFirst().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        });
    }

    @Test
    void lateOnlineApprovalIsRefundedWithoutReactivatingOrder() {
        long id = createOrder();
        var order = commandOrders.findById(id).orElseThrow();
        var payment = PaymentCommandEntity.forPix(order);
        String mpId = "late-" + id;
        payment.setMpPaymentId(mpId);
        payments.saveAndFlush(payment);
        cancellations.cancelOrder(id, 42L, "Cliente cancelou");
        var remote = mock(com.mercadopago.resources.payment.Payment.class);
        when(remote.getStatus()).thenReturn("approved");
        when(paymentGateway.get(mpId)).thenReturn(remote);
        webhook.processPayment(mpId);
        assertThat(commandOrders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payments.findByMpPaymentId(mpId).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentGateway).refund(mpId);
    }

    @Test
    void databaseRejectsTwoActivePaymentsForOneOrder() {
        long id = createOrder();
        var order = commandOrders.findById(id).orElseThrow();
        payments.saveAndFlush(PaymentCommandEntity.forPix(order));
        assertThatThrownBy(() -> payments.saveAndFlush(PaymentCommandEntity.forPix(order)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(payments.findByOrderOrderId(id)).hasSize(1);
    }
}
