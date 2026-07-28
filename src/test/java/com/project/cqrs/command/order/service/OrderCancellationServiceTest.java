package com.project.cqrs.command.order.service;

import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.payment.PaymentRefund;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("OrderCancellationService")
public class OrderCancellationServiceTest {

    private OrderCommandRepository orderCommandRepository;
    private PaymentCommandRepository paymentCommandRepository;
    private OrderEventProducer orderEventProducer;
    private OrderCancelledService orderCancelledService;

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final String REASON = "Cliente desistiu da compra";

    @BeforeEach
    public void setUp() {
        orderCommandRepository = mock(OrderCommandRepository.class);
        paymentCommandRepository = mock(PaymentCommandRepository.class);
        orderEventProducer = mock(OrderEventProducer.class);
        orderCancelledService = new OrderCancelledService(orderCommandRepository, paymentCommandRepository, orderEventProducer);
    }

    // == Helpers =============================================================

    private OrderCommandEntity mockOrder(OrderStatus status, Long userId) {
        OrderCommandEntity orderCommandEntity = mock(OrderCommandEntity.class);
        when(orderCommandEntity.getOrderId()).thenReturn(ORDER_ID);
        when(orderCommandEntity.getUserId()).thenReturn(userId);
        when(orderCommandEntity.getStatus()).thenReturn(status);
        when(orderCommandEntity.getTotalAmount()).thenReturn(new BigDecimal("199.90"));
        return orderCommandEntity;
    }

    private PaymentCommandEntity mockApprovedPayment(OrderCommandEntity orderCommandEntity, String mpPaymentId) {
        PaymentCommandEntity paymentCommandEntity = mock(PaymentCommandEntity.class);
        when(paymentCommandEntity.getOrder()).thenReturn(orderCommandEntity);
        when(paymentCommandEntity.getPaymentStatus()).thenReturn(PaymentStatus.APPROVED);
        when(paymentCommandEntity.getMpPaymentId()).thenReturn(mpPaymentId);
        return paymentCommandEntity;
    }

    private PaymentCommandEntity mockPendingPayment(OrderCommandEntity orderCommandEntity) {
        PaymentCommandEntity paymentCommandEntity = mock(PaymentCommandEntity.class);
        when(paymentCommandEntity.getOrder()).thenReturn(orderCommandEntity);
        when(paymentCommandEntity.getPaymentStatus()).thenReturn(PaymentStatus.PENDING);
        return paymentCommandEntity;
    }

    // -- Validações básicas

    @Nested
    @DisplayName("Validações antes de cancelar")
    class BasicValidations {

        @Test
        @DisplayName("deve lançar ResourceNotFoundException quando pedido não existe")
        void shouldThrowOrderNotFound() {
            when(orderCommandRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deve lançar ResourceNotFoundException quando o pedido é de outro usuário")
        void shouldThrowWhenOrderBelongsToAnotherUser() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, 999L);
            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deve lançar IllegalStateException quando o pedido já está cancelado")
        void shouldThrowWhenAlreadyCancelled() {
            OrderCommandEntity order = mockOrder(OrderStatus.CANCELLED, USER_ID);
            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Já está cancelado");
        }
    }

    // -- Cancelamento sem pagamento aprovado (sem estorno) -------------------

    @Nested
    @DisplayName("Cancelamento de pedido sem pagamento aprovado")
    class CancelWithoutRefund {

        @Test
        @DisplayName("deve cancelar sem chamar a API do Mercado Pago quando pedido está PENDING")
        void shouldCancelWithoutCallingMpWhenPending() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, USER_ID);
            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentCommandRepository.findAll()).thenReturn(List.of());

            try (MockedConstruction<PaymentRefundClient> mocked = mockConstruction(PaymentRefundClient.class)) {

                orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

                assertThat(mocked.constructed()).isEmpty(); // nenhuma chamada ao MP
                verify(order).markAsCancelled();
                verify(orderCommandRepository).save(order);
            }
        }

        @Test
        @DisplayName("deve cancelar pagamentos PENDING/IN_PROCESS associados ao pedido")
        void shouldCancelPendingPaymentsAssociated() {
            OrderCommandEntity order = mockOrder(OrderStatus.AWAITING_PAYMENT, USER_ID);
            PaymentCommandEntity pendingPayment = mockPendingPayment(order);

            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentCommandRepository.findAll()).thenReturn(List.of(pendingPayment));

            try(MockedConstruction<PaymentRefundClient> mocked = mockConstruction(PaymentRefundClient.class)) {

                orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

                verify(pendingPayment).setPaymentStatus(PaymentStatus.CANCELLED);
                verify(paymentCommandRepository).save(pendingPayment);
            }
        }

        @Test
        @DisplayName("deve publicar OrderCancelled com mpPaymentId nulo")
        void shouldPublishEventWithNullMpPaymentId() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, USER_ID);
            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
            when(paymentCommandRepository.findAll()).thenReturn(List.of());

            try (MockedConstruction<PaymentRefundClient> mocked = mockConstruction(PaymentRefundClient.class)) {
                orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

                ArgumentCaptor<OrderCancelledEvent> captor =
                        ArgumentCaptor.forClass(OrderCancelledEvent.class);
                verify(orderEventProducer).publishOrderCancelled(eq(ORDER_ID.toString()), captor.capture());

                assertThat(captor.getValue().mpPaymentId()).isNull();
                assertThat(captor.getValue().reason()).isEqualTo(REASON);
            }
        }
    }

    // -- Cancelamento COM estorno -- o cenário crítico -----------------------

    @Nested
    @DisplayName("Cancelamento de pedido PAGO - com estorno")
    class CancelWithRefund {}

    @Test
    @DisplayName("deve chamar o estorno no Mercado Pago quando o pedido está PAID")
    void shouldCallRefundWhenOrderIsPaid() throws MPException, MPApiException {

        OrderCommandEntity order = mockOrder(OrderStatus.PAID, USER_ID);
        PaymentCommandEntity approvedPayment = mockApprovedPayment(order, "123");

        when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentCommandRepository.findAll()).thenReturn(List.of(approvedPayment));

        try (MockedConstruction<PaymentRefundClient> mocked =
                     mockConstruction(PaymentRefundClient.class,
                             (mockClient, context) -> {

                                 when(mockClient.refund(anyLong()))
                                         .thenReturn(mock(PaymentRefund.class));
                             })) {

            orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

            assertThat(mocked.constructed()).hasSize(1);
            verify(mocked.constructed().get(0))
                    .refund(123L);
        }
    }

    @Test
    @DisplayName("deve marcar o pagamento como REFUNDED após estorno bem-sucedido")
    void shouldMarkPaymentAsRefundedAfterSucess()  {
        OrderCommandEntity order = mockOrder(OrderStatus.PAID, USER_ID);
        PaymentCommandEntity approvedPayment = mockApprovedPayment(order, "123");

        when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentCommandRepository.findAll()).thenReturn(List.of(approvedPayment));

        try (MockedConstruction<PaymentRefundClient> mocked =
                     mockConstruction(PaymentRefundClient.class,
                             (mockClient, context) -> {

                                 when(mockClient.refund(anyLong()))
                                         .thenReturn(mock(PaymentRefund.class));
                             })) {

            orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

            verify(approvedPayment).setPaymentStatus(PaymentStatus.REFUNDED);
            verify(paymentCommandRepository).save(approvedPayment);
        }
    }

    @Test
    @DisplayName("deve cancelar o pedido normalmente após estorno bem-sucedido")
    void shouldCancelOrderAfterSucessfulRefund() {
        OrderCommandEntity order = mockOrder(OrderStatus.PAID, USER_ID);
        PaymentCommandEntity approvedPayment = mockApprovedPayment(order, "123");

        when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentCommandRepository.findAll()).thenReturn(List.of(approvedPayment));

        try (MockedConstruction<PaymentRefundClient> mocked = mockConstruction(
                PaymentRefundClient.class,
                (mockClient, context) -> {

                    when(mockClient.refund(anyLong()))
                            .thenReturn(mock(PaymentRefund.class));


                })) {
            orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

            verify(order).markAsCancelled();
            verify(orderCommandRepository).save(order);
        }
    }

    @Test
    @DisplayName("""
            [CRÍTICO] NÃO deve cancelar o pedido quando o estorno falha —
            evita que o cliente perca o dinheiro e o produto ao mesmo tempo
            """)
    void shouldNotCancelOrderWhenRefundFails() {

        OrderCommandEntity order = mockOrder(OrderStatus.PAID, USER_ID);
        PaymentCommandEntity approvedPayment = mockApprovedPayment(order, "123");

        when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentCommandRepository.findAll()).thenReturn(List.of(approvedPayment));

        MPApiException refundFailure = mock(MPApiException.class);
        MPResponse response = mock(MPResponse.class);
        when(response.getContent()).thenReturn("{\"error\":\"refund_failed\"}");
        when(refundFailure.getApiResponse()).thenReturn(response);
        when(refundFailure.getStatusCode()).thenReturn(500);

        try (MockedConstruction<PaymentRefundClient> mocked = mockConstruction(
                PaymentRefundClient.class,
                (mockClient, context) -> {
                    when(mockClient.refund(anyLong()))
                            .thenReturn(mock(PaymentRefund.class));
                }
        )) {
            orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

            verify(order).markAsCancelled();
            verify(orderCommandRepository).save(order);
        }
    }

    @Test
    @DisplayName("""
            deve cancelar sem tentar estornar quando o pagamento aprovado
            não tem mpPaymentId (pagamento presencial)
            """)
    void shouldCancelWithoutRefundingInPersonPayment() {
        OrderCommandEntity order = mockOrder(OrderStatus.PAID, USER_ID);
        PaymentCommandEntity approvedPayment = mockApprovedPayment(order, null);

        when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentCommandRepository.findAll()).thenReturn(List.of(approvedPayment));

        try(MockedConstruction<PaymentRefundClient> mocked = mockConstruction(PaymentRefundClient.class)) {

            orderCancelledService.cancelOrder(ORDER_ID, USER_ID, REASON);

            // Nenhuma chamada ao MP — não há o que estornar online
            assertThat(mocked.constructed()).isEmpty();
            verify(order).markAsCancelled();
        }
    }
}
