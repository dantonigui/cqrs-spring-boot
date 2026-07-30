package com.project.cqrs.command.payment.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.net.MPResponse;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.payment.dto.request.CardCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.request.InPersonCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.response.CardPaymentResponseDTO;
import com.project.cqrs.command.payment.dto.response.InPersonPaymentResponseDTO;
import com.project.cqrs.command.payment.dto.response.PixPaymentResponseDTO;
import com.project.cqrs.config.exception.PaymentException;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.dto.request.PixCheckoutRequestDTO;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.shared.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes do MercadoPagoPaymentService — a lógica mais rica do projeto,
 * com todas as guardas contra duplo pagamento.
 *
 * TÉCNICA DE TESTE: o service faz `new PaymentClient()` diretamente
 * dentro dos métodos (não é injetado via construtor). Para testar sem
 * chamar a API real do Mercado Pago, usamos Mockito.mockConstruction(),
 * que intercepta QUALQUER "new PaymentClient()" durante o escopo do
 * try-with-resources e redireciona para um mock configurável.
 *
 * Isso evita precisar refatorar o código de produção só para testar —
 * mas se no futuro o PaymentClient for injetado via construtor
 * (melhoria de testabilidade), estes testes podem ser simplificados
 * trocando mockConstruction por um @Mock comum.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MercadoPagoPaymentService")
class MercadoPagoPaymentServiceTest {

    private OrderCommandRepository   orderRepository;
    private PaymentCommandRepository paymentRepository;
    private MercadoPagoPaymentService service;

    private static final Long ORDER_ID = 1L;
    private static final String BASE_URL = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        orderRepository   = mock(OrderCommandRepository.class);
        paymentRepository = mock(PaymentCommandRepository.class);
        service = new MercadoPagoPaymentService(orderRepository, paymentRepository);
        ReflectionTestUtils.setField(service, "appBaseUrl", BASE_URL);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OrderCommandEntity mockOrder(OrderStatus status, BigDecimal total) {
        OrderCommandEntity order = mock(OrderCommandEntity.class);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getStatus()).thenReturn(status);
        when(order.getTotalAmount()).thenReturn(total);
        when(order.getItems()).thenReturn(List.of());
        return order;
    }

    private PixCheckoutRequestDTO pixRequest() {
        return new PixCheckoutRequestDTO(
                "cliente@teste.com", "João", "Silva", "12345678900");
    }

    private CardCheckoutRequestDTO cardRequest(int installments) {
        return new CardCheckoutRequestDTO(
                "card-token-abc", installments,
                "cliente@teste.com", "12345678900", "visa");
    }

    private InPersonCheckoutRequestDTO inPersonRequest(String method) {
        return new InPersonCheckoutRequestDTO(method);
    }

    /** Configura um mock de Payment (resposta do MP) para o cenário de sucesso PIX. */
    private void stubSuccessfulPixResponse(Payment mpPaymentMock) {
        when(mpPaymentMock.getId()).thenReturn(999L);
        when(mpPaymentMock.getStatus()).thenReturn(
                com.mercadopago.resources.payment.PaymentStatus.PENDING);

        var pointOfInteraction = mock(
                com.mercadopago.resources.payment.PaymentPointOfInteraction.class,
                RETURNS_DEEP_STUBS);
        when(pointOfInteraction.getTransactionData().getQrCode())
                .thenReturn("00020126...pix-copia-e-cola");
        when(pointOfInteraction.getTransactionData().getQrCodeBase64())
                .thenReturn("aGVsbG8td29ybGQ=");
        when(mpPaymentMock.getPointOfInteraction()).thenReturn(pointOfInteraction);
    }

    private void stubCardResponse(Payment mpPaymentMock,
                                  PaymentStatus status) {
        when(mpPaymentMock.getId()).thenReturn(888L);
        when(mpPaymentMock.getStatus()).thenReturn(status.name());
        when(mpPaymentMock.getStatusDetail()).thenReturn("accredited");
        var card = mock(com.mercadopago.resources.payment.PaymentCard.class);
        when(card.getLastFourDigits()).thenReturn("1234");
        when(mpPaymentMock.getCard()).thenReturn(card);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIX
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createPixPayment()")
    class Pix {

        @Test
        @DisplayName("deve lançar ResourceNotFoundException quando pedido não existe")
        void shouldThrowWhenOrderNotFound() {
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.createPixPayment(ORDER_ID, pixRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deve rejeitar quando o pedido já está PAID")
        void shouldRejectWhenOrderAlreadyPaid() {
            OrderCommandEntity order = mockOrder(OrderStatus.PAID, new BigDecimal("100.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    service.createPixPayment(ORDER_ID, pixRequest()))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("já foi pago");
        }

        @Test
        @DisplayName("deve rejeitar quando o pedido está CANCELLED")
        void shouldRejectWhenOrderCancelled() {
            OrderCommandEntity order = mockOrder(OrderStatus.CANCELLED, new BigDecimal("100.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    service.createPixPayment(ORDER_ID, pixRequest()))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("cancelado");
        }

        @Test
        @DisplayName("""
            deve reenviar o QR code existente sem chamar o Mercado Pago
            quando já há um PIX pendente para o pedido
            """)
        void shouldReuseExistingPendingPixWithoutCallingMp() {
            OrderCommandEntity order = mockOrder(
                    OrderStatus.AWAITING_PAYMENT, new BigDecimal("150.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            PaymentCommandEntity existingPix = mock(PaymentCommandEntity.class);
            when(existingPix.getId()).thenReturn(50L);
            when(existingPix.getPaymentStatus()).thenReturn(PaymentStatus.PENDING);
            when(existingPix.getPixQrCode()).thenReturn("qr-code-existente");
            when(existingPix.getPixQrCodeBase64()).thenReturn("base64-existente");
            when(existingPix.getPixExpiration())
                    .thenReturn(LocalDateTime.now().plusMinutes(20));

            when(paymentRepository.findPendingPixByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(existingPix));

            try (MockedConstruction<PaymentClient> mocked =
                         mockConstruction(PaymentClient.class)) {

                PixPaymentResponseDTO result =
                        service.createPixPayment(ORDER_ID, pixRequest());

                assertThat(result.pixQrCode()).isEqualTo("qr-code-existente");
                // PaymentClient NUNCA deve ter sido construído/chamado —
                // é a prova de que não gerou um segundo pagamento no MP
                assertThat(mocked.constructed()).isEmpty();
            }
        }

        @Test
        @DisplayName("deve criar novo PIX no Mercado Pago quando não há pendente")
        void shouldCreateNewPixWhenNoneIsPending() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("150.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            try (MockedConstruction<PaymentClient> mocked = mockConstruction(
                    PaymentClient.class,
                    (mockClient, context) -> {
                        Payment mpResponse = mock(Payment.class);
                        stubSuccessfulPixResponse(mpResponse);
                        when(mockClient.create(any(), any())).thenReturn(mpResponse);
                    })) {

                PixPaymentResponseDTO result =
                        service.createPixPayment(ORDER_ID, pixRequest());

                assertThat(result.orderId()).isEqualTo(ORDER_ID);
                assertThat(result.pixQrCode()).isEqualTo("00020126...pix-copia-e-cola");
                assertThat(result.amount()).isEqualByComparingTo("150.00");

                verify(order).markAsAwaitingPayment();
                verify(paymentRepository, atLeastOnce()).save(any(PaymentCommandEntity.class));
            }
        }

        @Test
        @DisplayName("deve marcar pagamento como rejeitado quando o MP retorna erro de API")
        void shouldRejectPaymentOnMpApiException() throws Exception {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("150.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            MPApiException apiException = mock(MPApiException.class);
            MPResponse apiResponse = mock(MPResponse.class);
            when(apiResponse.getContent()).thenReturn("{\"error\":\"invalid_token\"}");
            when(apiException.getApiResponse()).thenReturn(apiResponse);
            when(apiException.getStatusCode()).thenReturn(401);

            try (MockedConstruction<PaymentClient> mocked = mockConstruction(
                    PaymentClient.class,
                    (mockClient, context) ->
                            when(mockClient.create(any(), any()))
                                    .thenThrow(apiException))) {

                assertThatThrownBy(() ->
                        service.createPixPayment(ORDER_ID, pixRequest()))
                        .isInstanceOf(PaymentException.class);

                // Confirma que o pagamento foi marcado como rejeitado no banco
                verify(paymentRepository, atLeastOnce()).save(any(PaymentCommandEntity.class));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CARTÃO
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createCardPayment()")
    class Card {

        @Test
        @DisplayName("deve rejeitar quando já existe pagamento ativo para o pedido")
        void shouldRejectWhenActivePaymentExists() {
            OrderCommandEntity order = mockOrder(
                    OrderStatus.AWAITING_PAYMENT, new BigDecimal("200.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.hasActivePayment(ORDER_ID)).thenReturn(true);

            assertThatThrownBy(() ->
                    service.createCardPayment(ORDER_ID, cardRequest(1)))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("em andamento");
        }

        @Test
        @DisplayName("deve marcar pedido como PAID quando o cartão é aprovado")
        void shouldMarkOrderPaidWhenApproved() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("200.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.hasActivePayment(ORDER_ID)).thenReturn(false);

            try (MockedConstruction<PaymentClient> mocked = mockConstruction(
                    PaymentClient.class,
                    (mockClient, context) -> {
                        Payment mpResponse = mock(Payment.class);
                        stubCardResponse(mpResponse,
                                PaymentStatus.APPROVED);
                        when(mockClient.create(any(), any())).thenReturn(mpResponse);
                    })) {

                CardPaymentResponseDTO result =
                        service.createCardPayment(ORDER_ID, cardRequest(3));

                assertThat(result.status()).isEqualTo("APPROVED");
                assertThat(result.installments()).isEqualTo(3);
                verify(order).markAsPaid();
            }
        }

        @Test
        @DisplayName("não deve marcar pedido como PAID quando o cartão está em análise (IN_PROCESS)")
        void shouldNotMarkPaidWhenInProcess() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("200.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.hasActivePayment(ORDER_ID)).thenReturn(false);

            try (MockedConstruction<PaymentClient> mocked = mockConstruction(
                    PaymentClient.class,
                    (mockClient, context) -> {
                        Payment mpResponse = mock(Payment.class);
                        stubCardResponse(mpResponse,
                                PaymentStatus.IN_PROCESS);
                        when(mockClient.create(any(), any())).thenReturn(mpResponse);
                    })) {

                service.createCardPayment(ORDER_ID, cardRequest(1));

                verify(order, never()).markAsPaid();
            }
        }

        @Test
        @DisplayName("não deve marcar pedido como PAID quando o cartão é rejeitado")
        void shouldNotMarkPaidWhenRejected() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("200.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.hasActivePayment(ORDER_ID)).thenReturn(false);

            try (MockedConstruction<PaymentClient> mocked = mockConstruction(
                    PaymentClient.class,
                    (mockClient, context) -> {
                        Payment mpResponse = mock(Payment.class);
                        stubCardResponse(mpResponse,
                                PaymentStatus.REJECTED);
                        when(mockClient.create(any(), any())).thenReturn(mpResponse);
                    })) {

                CardPaymentResponseDTO result =
                        service.createCardPayment(ORDER_ID, cardRequest(1));

                assertThat(result.status()).isEqualTo("REJECTED");
                verify(order, never()).markAsPaid();
            }
        }

        @Test
        @DisplayName("deve rejeitar quando o pedido já está PAID")
        void shouldRejectWhenAlreadyPaid() {
            OrderCommandEntity order = mockOrder(OrderStatus.PAID, new BigDecimal("200.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    service.createCardPayment(ORDER_ID, cardRequest(1)))
                    .isInstanceOf(PaymentException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRESENCIAL
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("registerInPersonPayment()")
    class InPerson {

        @Test
        @DisplayName("deve aprovar imediatamente sem chamar a API do Mercado Pago")
        void shouldApproveImmediatelyWithoutCallingMp() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("80.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.hasActivePayment(ORDER_ID)).thenReturn(false);

            try (MockedConstruction<PaymentClient> mocked =
                         mockConstruction(PaymentClient.class)) {

                InPersonPaymentResponseDTO result = service
                        .registerInPersonPayment(ORDER_ID, inPersonRequest("CASH"));

                assertThat(result.status()).isEqualTo("APPROVED");
                assertThat(result.method()).isEqualTo("CASH");
                verify(order).markAsPaid();

                // Nenhuma chamada ao SDK do Mercado Pago
                assertThat(mocked.constructed()).isEmpty();
            }
        }

        @Test
        @DisplayName("deve rejeitar quando já existe pagamento ativo")
        void shouldRejectWhenActivePaymentExists() {
            OrderCommandEntity order = mockOrder(OrderStatus.PENDING, new BigDecimal("80.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));
            when(orderRepository.hasActivePayment(ORDER_ID)).thenReturn(true);

            assertThatThrownBy(() -> service
                    .registerInPersonPayment(ORDER_ID, inPersonRequest("CARD")))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("já possui pagamento registrado");
        }

        @Test
        @DisplayName("deve rejeitar quando o pedido já está PAID")
        void shouldRejectWhenAlreadyPaid() {
            OrderCommandEntity order = mockOrder(OrderStatus.PAID, new BigDecimal("80.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service
                    .registerInPersonPayment(ORDER_ID, inPersonRequest("PIX")))
                    .isInstanceOf(PaymentException.class);
        }

        @Test
        @DisplayName("deve rejeitar quando o pedido está CANCELLED")
        void shouldRejectWhenCancelled() {
            OrderCommandEntity order = mockOrder(OrderStatus.CANCELLED, new BigDecimal("80.00"));
            when(orderRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service
                    .registerInPersonPayment(ORDER_ID, inPersonRequest("CASH")))
                    .isInstanceOf(PaymentException.class);
        }
    }
}