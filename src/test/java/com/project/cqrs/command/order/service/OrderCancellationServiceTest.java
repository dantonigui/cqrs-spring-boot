package com.project.cqrs.command.order.service;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.command.payment.service.PaymentGateway;
import com.project.cqrs.command.product.service.InventoryService;
import com.project.cqrs.shared.enums.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class OrderCancellationServiceTest {
    OrderCommandRepository orders; PaymentCommandRepository payments; OrderEventProducer events; InventoryService inventory; PaymentGateway gateway; OrderCancelledService service; OrderCommandEntity order;
    @BeforeEach void setup() {
        orders = mock(OrderCommandRepository.class); payments = mock(PaymentCommandRepository.class); events = mock(OrderEventProducer.class); inventory = mock(InventoryService.class); gateway = mock(PaymentGateway.class);
        service = new OrderCancelledService(orders, payments, events, inventory, gateway);
        order = OrderCommandEntity.builder().orderId(1L).userId(2L).totalAmount(new BigDecimal("10.00")).build();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
    }
    @AfterEach void clearSecurity() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @Test void rejectsOtherOwner() { assertThatThrownBy(() -> service.cancelOrder(1L, 3L, "reason")).isInstanceOf(com.project.cqrs.config.exception.ResourceNotFoundException.class); verifyNoInteractions(inventory, events); }
    @Test void cancelsAndReleasesOnlyOnce() {
        service.cancelOrder(1L, 2L, "reason"); assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); verify(inventory).release(order.getItems());
        assertThatThrownBy(() -> service.cancelOrder(1L, 2L, "reason")).isInstanceOf(IllegalStateException.class); verify(inventory, times(1)).release(any());
    }
    @Test void cancelsPendingProviderPaymentBeforeReleasingStock() {
        var payment = PaymentCommandEntity.forPix(order); payment.setMpPaymentId("123"); when(payments.findByOrderOrderId(1L)).thenReturn(List.of(payment));
        service.cancelOrder(1L, 2L, "reason"); var sequence = inOrder(gateway, inventory); sequence.verify(gateway).cancelOrRefund("123"); sequence.verify(inventory).release(any());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
    @Test void refundFailureDoesNotReleaseStockOrCancel() {
        var payment = PaymentCommandEntity.forPix(order); payment.setMpPaymentId("123"); when(payments.findByOrderOrderId(1L)).thenReturn(List.of(payment));
        when(gateway.cancelOrRefund("123")).thenThrow(new com.project.cqrs.config.exception.PaymentException("failed"));
        assertThatThrownBy(() -> service.cancelOrder(1L, 2L, "reason")).isInstanceOf(com.project.cqrs.config.exception.PaymentException.class); verifyNoInteractions(inventory, events); assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
    @Test void requiresAdminForInPersonCancellation() {
        order.markAsPaid(); when(payments.findByOrderOrderId(1L)).thenReturn(List.of(PaymentCommandEntity.forInPerson(order, "CASH")));
        assertThatThrownBy(() -> service.cancelOrder(1L, 2L, "reason")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); verifyNoInteractions(inventory, events);
    }
    @Test void expirationCannotCancelPaidOrder() { order.markAsPaid(); service.expireOrder(1L, 2L); verifyNoInteractions(payments, inventory, events); }
}