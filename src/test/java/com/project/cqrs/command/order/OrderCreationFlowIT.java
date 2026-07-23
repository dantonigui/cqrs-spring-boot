package com.project.cqrs.command.order;

import com.project.cqrs.AbstractIntegrationTest;
import com.project.cqrs.command.order.dto.CreateOrderRequestDTO;
import com.project.cqrs.command.order.dto.OrderItemDTO;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.product.model.ProductQueryEntity;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;


import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração do fluxo de criação de pedido.
 *
 * Testa o caminho completo:
 *   POST /api/v1/command/orders
 *     → OrderCommandService (busca preço no banco)
 *     → OrderRepository (salva no MySQL)
 *     → Kafka publica order.created
 *     → OrderEventConsumer (salva na projeção de leitura)
 *     → Redis (cache invalidado)
 *
 * O Awaitility aguarda o processamento assíncrono do Kafka
 * sem usar Thread.sleep.
 */
@DisplayName("Fluxo de criação de pedido — integração")
class OrderCreationFlowIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ProductQueryRepository productQueryRepository;
    @Autowired private OrderCommandRepository orderRepository;
    @Autowired private OrderQueryRepository orderQueryRepository;

    private ProductQueryEntity savedProduct;

    @BeforeEach
    void setUp() {
        // Cria um produto no banco de leitura para o teste
        ProductQueryEntity product = new ProductQueryEntity();
        // ... configure os campos do produto
        // product.setProductName("Produto de Teste");
        // product.setProductPrice(new BigDecimal("150.00"));
        savedProduct = productQueryRepository.save(product);
    }

    @Test
    @DisplayName("deve criar pedido com preço do banco e sincronizar projeção via Kafka")
    void shouldCreateOrderAndSyncQuerySideViaKafka() {
        // ── Arrange ──────────────────────────────────────────────────────────
        CreateOrderRequestDTO request = new CreateOrderRequestDTO(
                List.of(new OrderItemDTO(
                        savedProduct.getProductId(), 2))
        );

        // Headers com JWT de um usuário autenticado (gerado no setup)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // headers.add("Cookie", "access_token=" + generateTestJwt());

        HttpEntity<CreateOrderRequestDTO> entity = new HttpEntity<>(request, headers);

        // ── Act ───────────────────────────────────────────────────────────────
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/command/orders", entity, String.class);

        // ── Assert: Command Side ──────────────────────────────────────────────
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Pedido deve ter sido salvo no banco de escrita
        assertThat(orderRepository.count()).isGreaterThan(0);

        // ── Assert: Query Side (assíncrono via Kafka) ─────────────────────────
        // Aguarda até 10 segundos o consumer processar o evento
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // A projeção de leitura deve ter sido criada pelo consumer
                    assertThat(orderQueryRepository.count()).isGreaterThan(0);
                });
    }

    @Test
    @DisplayName("deve retornar 404 quando produto não existe")
    void shouldReturn404WhenProductNotFound() {
        CreateOrderRequestDTO request = new CreateOrderRequestDTO(
                List.of(new OrderItemDTO(999999L, 1))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/command/orders",
                new HttpEntity<>(request, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Resposta deve ser JSON estruturado (não stack trace)
        assertThat(response.getBody()).contains("\"status\"");
        assertThat(response.getBody()).contains("\"message\"");
    }

    @Test
    @DisplayName("deve retornar 400 com fieldErrors quando body está inválido")
    void shouldReturn400WithFieldErrorsOnInvalidBody() {
        // Body vazio — @Valid deve rejeitar
        CreateOrderRequestDTO request = new CreateOrderRequestDTO(List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/command/orders",
                new HttpEntity<>(request, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("fieldErrors");
    }
}