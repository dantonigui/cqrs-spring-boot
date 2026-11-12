package com.project.cqrs.command.payment.service;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.command.payment.kafka.producer.PaymentEventProducer;
import com.project.cqrs.shared.enums.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PaymentApprovalServiceTest {
    OrderCommandRepository orders; PaymentEventProducer events; PaymentGateway gateway; PaymentApprovalService service;
    OrderCommandEntity order; PaymentCommandEntity payment;
    @BeforeEach void setup() {
        orders = mock(OrderCommandRepository.class); events = mock(PaymentEventProducer.class); gateway = mock(PaymentGateway.class);
        service = new PaymentApprovalService(orders, events, gateway);
        order = OrderCommandEntity.builder().orderId(1L).userId(2L).totalAmount(new BigDecimal("10.00")).build();
        payment = PaymentCommandEntity.forPix(order); payment.setMpPaymentId("123");
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    }
    @Test void approvesAndPublishes() {
        service.approve(payment, "123"); assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(events).publishPaymentApproved(eq("1"), argThat(e -> "123".equals(e.mpPaymentId()) && e.userId().equals(2L)));
    }
    @Test void neverRevivesCancelledOrderAndRefundsLateApproval() {
        order.markAsCancelled(); service.approve(payment, "123");
        verify(gateway).refund("123"); verifyNoInteractions(events); assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
    @Test void doesNotPublishTwice() { service.approve(payment, "123"); service.approve(payment, "123"); verify(events, times(1)).publishPaymentApproved(any(), any()); }
    @Test void refundFailurePropagates() {
        order.markAsCancelled(); doThrow(new com.project.cqrs.config.exception.PaymentException("failed")).when(gateway).refund("123");
        assertThatThrownBy(() -> service.approve(payment, "123")).isInstanceOf(com.project.cqrs.config.exception.PaymentException.class); verifyNoInteractions(events);
    }
}