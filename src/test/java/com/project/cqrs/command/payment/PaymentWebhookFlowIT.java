package com.project.cqrs.command.payment;

import com.project.cqrs.AbstractIntegrationTest;

import com.project.cqrs.command.order.repository.OrderCommandRepository;

import com.project.cqrs.command.payment.repository.PaymentCommandRepository;

import com.project.cqrs.query.order.repository.OrderQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o fluxo de webhook de pagamento:
 *
 *   POST /api/v1/command/payments/webhook
 *     → WebhookController (recebe, delega)
 *     → WebhookService (valida tipo, extrai ID)
 *     → MercadoPagoWebhookProcessor (consulta MP*, atualiza PaymentEntity)
 *     → PaymentApprovalService (lock, marca PAID, publica Kafka)
 *     → OrderEventConsumer (atualiza projeção de leitura)
 *
 * * Em teste: MP_ACCESS_TOKEN=TEST, então não faz chamada real.
 *   Usamos @MockBean em PaymentClient para simular a resposta.
 */
@DisplayName("Fluxo de webhook de pagamento — integração")
class PaymentWebhookFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate    restTemplate;
    @Autowired private OrderCommandRepository orderRepository;
    @Autowired private PaymentCommandRepository   paymentRepository;
    @Autowired private OrderQueryRepository orderQueryRepository;

    @Test
    @DisplayName("webhook deve responder 200 sempre (mesmo com assinatura ausente em dev)")
    void shouldAlwaysReturn200() {
        Map<String, Object> body = Map.of(
                "type", "payment",
                "data", Map.of("id", "123456")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/command/payments/webhook",
                new HttpEntity<>(body, headers),
                Void.class);

        // Sempre 200 — o MP não deve retentar
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("webhook com tipo diferente de payment deve ser ignorado")
    void shouldIgnoreNonPaymentWebhookType() {
        Map<String, Object> body = Map.of(
                "type", "merchant_order",
                "data", Map.of("id", "789")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/command/payments/webhook",
                new HttpEntity<>(body, headers),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Nenhum pagamento deve ter sido processado
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    @DisplayName("endpoints admin de cache devem retornar 403 para usuário não ADMIN")
    void shouldReturn403ForNonAdminOnCacheEndpoints() {
        // Sem token (não autenticado)
        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/cache/all",
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class);

        // Deve ser 401 (não autenticado) ou 403 (sem role ADMIN)
        assertThat(response.getStatusCode().value())
                .isIn(401, 403);
    }

    @Test
    @DisplayName("endpoints admin de DLQ devem retornar 401/403 sem autenticação")
    void shouldReturn401Or403ForNonAdminOnDlqEndpoints() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/admin/dlq/stats", String.class);

        assertThat(response.getStatusCode().value())
                .isIn(401, 403);
    }
}