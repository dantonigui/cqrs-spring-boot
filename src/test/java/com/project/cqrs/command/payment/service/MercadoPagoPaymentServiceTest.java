package com.project.cqrs.command.payment.service;
import com.mercadopago.client.payment.*;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.command.payment.dto.request.*;
import com.project.cqrs.config.exception.*;
import com.project.cqrs.shared.enums.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class MercadoPagoPaymentServiceTest {
    OrderCommandRepository orders; PaymentCommandRepository payments; PaymentApprovalService approvals; MercadoPagoPaymentService service; OrderCommandEntity order;
    PixCheckoutRequestDTO pix = new PixCheckoutRequestDTO("user@example.com", "João", "Silva", "12345678900");
    CardCheckoutRequestDTO card = new CardCheckoutRequestDTO("card-token", 1, "user@example.com", "12345678900", "visa");
    @BeforeEach void setup() {
        orders = mock(OrderCommandRepository.class); payments = mock(PaymentCommandRepository.class); approvals = mock(PaymentApprovalService.class);
        service = new MercadoPagoPaymentService(orders, payments, approvals); ReflectionTestUtils.setField(service, "baseUrl", "https://example.com");
        order = OrderCommandEntity.builder().orderId(1L).userId(2L).totalAmount(new BigDecimal("10.00")).build();
        when(orders.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(payments.save(any())).thenAnswer(i -> { var p = (PaymentCommandEntity)i.getArgument(0); ReflectionTestUtils.setField(p, "id", 9L); return p; });
    }
    @Test void pixRequiresOwner() { assertThatThrownBy(() -> service.createPixPayment(1L, 3L, pix)).isInstanceOf(ResourceNotFoundException.class); verify(payments, never()).save(any()); }
    @Test void cardRequiresOwner() { assertThatThrownBy(() -> service.createCardPayment(1L, 3L, card)).isInstanceOf(ResourceNotFoundException.class); verify(payments, never()).save(any()); }
    @Test void rejectsPaidAndCancelledOrders() {
        order.markAsPaid(); assertThatThrownBy(() -> service.createPixPayment(1L, 2L, pix)).isInstanceOf(PaymentException.class);
        order.markAsCancelled(); assertThatThrownBy(() -> service.createCardPayment(1L, 2L, card)).isInstanceOf(PaymentException.class);
    }
    @Test void reusesPendingPixWithoutCallingProvider() {
        order.markAsAwaitingPayment(); var p = PaymentCommandEntity.forPix(order); p.setPixData("qr", "base64", LocalDateTime.now().plusMinutes(30));
        when(orders.hasActivePayment(1L)).thenReturn(true); when(payments.findPendingPixByOrderId(1L)).thenReturn(Optional.of(p));
        try (var clients = mockConstruction(PaymentClient.class)) {
            assertThat(service.createPixPayment(1L, 2L, pix).pixQrCode()).isEqualTo("qr"); assertThat(clients.constructed()).isEmpty();
        }
    }
    @Test void rejectsPixWhenCardIsActive() {
        when(orders.hasActivePayment(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.createPixPayment(1L, 2L, pix)).isInstanceOf(PaymentException.class);
    }
    @Test void rejectsCardWhenAnotherPaymentIsActive() {
        when(orders.hasActivePayment(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.createCardPayment(1L, 2L, card)).isInstanceOf(PaymentException.class);
    }
    @Test void cardApprovalUsesSharedApprovalAndPublishesThroughIt() throws Exception {
        var mp = mock(Payment.class); when(mp.getId()).thenReturn(123L); when(mp.getStatus()).thenReturn("approved");
        try (var clients = mockConstruction(PaymentClient.class, (client, context) -> when(client.create(any(), any(MPRequestOptions.class))).thenReturn(mp))) {
            service.createCardPayment(1L, 2L, card); verify(approvals).approve(any(PaymentCommandEntity.class), eq("123"));
            var request = org.mockito.ArgumentCaptor.forClass(PaymentCreateRequest.class);
            verify(clients.constructed().get(0)).create(request.capture(), any());
            assertThat(request.getValue().getNotificationUrl()).isEqualTo("https://example.com/api/v1/webhooks/mercadopago");
            assertThat(request.getValue().getTransactionAmount()).isEqualByComparingTo("10.00");
        }
    }
    @Test void inPersonUsesSharedApproval() {
        service.registerInPersonPayment(1L, new InPersonCheckoutRequestDTO("CASH"));
        verify(payments).saveAndFlush(any()); verify(approvals).approve(any(), isNull());
    }
}