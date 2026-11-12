package com.project.cqrs.command.order;
import com.project.cqrs.AbstractIntegrationTest;
import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import com.project.cqrs.command.product.repository.ProductRepository;
import com.project.cqrs.command.order.service.OrderCommandService;
import com.project.cqrs.command.order.dto.*;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
class OrderCreationFlowIT extends AbstractIntegrationTest {
    @Autowired MockMvc mvc; @Autowired CategoryRepository categories; @Autowired ProductRepository products;
    @Autowired OrderCommandService orders; @Autowired OrderQueryRepository projections; @Autowired OrderQueryService queries;
    @Autowired com.project.cqrs.command.order.repository.OrderCommandRepository commandOrders;
    @Autowired com.project.cqrs.command.order.service.OrderExpirationService expiration;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("commandDataSource") javax.sql.DataSource commandDataSource;
    @Autowired com.project.cqrs.command.product.kafka.producer.ProductEventProducer productEvents;
    ProductCommandEntity product;
    private org.springframework.test.web.servlet.request.RequestPostProcessor actor(String id) {
        return authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                id, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }
    @BeforeEach void setup() {
        var category = categories.save(CategoryCommandEntity.createCategory("Categoria"));
        product = ProductCommandEntity.createProduct("Produto teste", UUID.randomUUID().toString(), new BigDecimal("10.00"), "https://example.com/image.png", category);
        product.replenishStock(3); product = products.save(product);
        productEvents.sendProductCreated(product.getProductId().toString(), com.project.cqrs.shared.event.product.ProductCreateEvent.fromEntity(product));
    }
    @Test void createsAndProjectsOrderThenProtectsCachedDetail() throws Exception {
        var response = mvc.perform(post("/api/v1/command/orders").with(actor("42")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":"+product.getProductId()+",\"quantity\":2}]}")).andExpect(status().isCreated()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("orderId").asLong();
        org.awaitility.Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> assertThat(projections.findByOrderId(id)).isPresent());
        assertThat(products.findById(product.getProductId()).orElseThrow().getStock()).isEqualTo(1);
        mvc.perform(get("/api/v1/query/orders/"+id).with(actor("42"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/query/orders/"+id).with(actor("42"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/query/orders/"+id).with(actor("99"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/query/orders").with(actor("42"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/query/orders").with(actor("42"))).andExpect(status().isOk());
    }
    @Test void rejectsMissingProductAndInvalidRequest() throws Exception {
        mvc.perform(post("/api/v1/command/orders").with(actor("42")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":9999999,\"quantity\":1}]}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/command/orders").with(actor("42")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")).andExpect(status().isBadRequest());
    }
    @Test void concurrentOrdersCannotOversell() throws Exception {
        var request = new CreateOrderRequestDTO(List.of(new OrderItemDTO(product.getProductId(), 2)));
        var wins = new java.util.concurrent.atomic.AtomicInteger(); var failures = new java.util.concurrent.atomic.AtomicInteger(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<?>>();
            for (int i=0; i<2; i++) tasks.add(executor.submit(() -> {
                try { start.await(); orders.createOrder(42L, request); wins.incrementAndGet(); }
                catch (com.project.cqrs.config.exception.InsufficientStockException e) { failures.incrementAndGet(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            start.countDown(); for(var task: tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(wins.get()).isEqualTo(1); assertThat(failures.get()).isEqualTo(1); assertThat(products.findById(product.getProductId()).orElseThrow().getStock()).isEqualTo(1);
    }
    @Test void expirationReleasesReservedStockOnce() {
        long id = orders.createOrder(42L, new CreateOrderRequestDTO(List.of(new OrderItemDTO(product.getProductId(), 2))));
        new org.springframework.jdbc.core.JdbcTemplate(commandDataSource).update(
                "UPDATE order_command SET created_at = ? WHERE order_id = ?",
                java.time.LocalDateTime.now().minusMinutes(31), id);
        expiration.expire();
        assertThat(commandOrders.findById(id).orElseThrow().getStatus()).isEqualTo(com.project.cqrs.shared.enums.OrderStatus.CANCELLED);
        assertThat(products.findById(product.getProductId()).orElseThrow().getStock()).isEqualTo(3);
        expiration.expire();
        assertThat(products.findById(product.getProductId()).orElseThrow().getStock()).isEqualTo(3);
    }
}
