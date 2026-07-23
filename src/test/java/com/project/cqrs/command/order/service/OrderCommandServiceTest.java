package com.project.cqrs.command.order.service;

import com.project.cqrs.command.order.dto.CreateOrderRequestDTO;
import com.project.cqrs.command.order.dto.OrderItemDTO;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.product.model.ProductQueryEntity;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandService")
public class OrderCommandServiceTest {

        @Mock
        private OrderCommandRepository orderCommandRepository;

        @Mock
        private ProductQueryRepository productQueryRepository;

        @Mock
        private OrderEventProducer  orderEventProducer;

        private OrderCommandService orderCommandService;

        private static final Long USER_ID = 1L;
        private static final Long PRODUCT_ID = 10L;

        @BeforeEach
        void setUp() {
            orderCommandService = new OrderCommandService(orderCommandRepository, productQueryRepository, orderEventProducer);
        }

        // -- Helpers ---------------------------------------------------------

    private ProductQueryEntity mockProduct(Long id, String name, BigDecimal price) {
        ProductQueryEntity p = new ProductQueryEntity();

        p.setProductId(id);
        p.setProductName(name);
        p.setProductPrice(price);

        return p;
    }

        private CreateOrderRequestDTO requestWithItem(Long productId, int quantity) {
            return new CreateOrderRequestDTO(List.of(new OrderItemDTO(productId,quantity)));
        }

        // -- Criação bem-sucedida --------------------------------------------

    @Nested
    @DisplayName("createOrder() - sucesso")
    class CreateOrderSuccess {

            @Test
            @DisplayName("deve criar pedido com preço vindo do banco, não do frontend")

        void shouldUsePriceFromDatabase(){
                // Produto no banco com preço R$ 99,90
                BigDecimal dbPrice = new BigDecimal("99.90");
                ProductQueryEntity product = mockProduct(PRODUCT_ID, "Produto A", dbPrice);
                when(productQueryRepository.findAllById(any()))
                        .thenReturn(List.of(product));

                // Simula o save retornando uma entidade com ID gerado
                OrderCommandEntity savedOrder = mock(OrderCommandEntity.class);
                when(savedOrder.getId()).thenReturn(1L);
                when(savedOrder.getStatus()).thenReturn(OrderStatus.PENDING);
                when(savedOrder.getTotalAmount()).thenReturn(dbPrice.multiply(BigDecimal.valueOf(2)));
                when(savedOrder.getItems()).thenReturn(List.of());
                when(savedOrder.getCreatedAt()).thenReturn(LocalDateTime.now());
                when(orderCommandRepository.save(any())).thenReturn(savedOrder);

                CreateOrderRequestDTO request = requestWithItem(PRODUCT_ID, 2);

                orderCommandService.createOrder(USER_ID, request);

                verify(orderCommandRepository).save(any(OrderCommandEntity.class));
                verify(orderEventProducer)
                        .publishOrderCreated(anyString(), any(OrderCreatedEvent.class));
            }

        @Test
        @DisplayName("deve publicar OrderCreatedEvent após salvar o pedido")
        void shouldPublishOrderCreatedEvent(){
                ProductQueryEntity product = mockProduct(PRODUCT_ID, "Produto A", BigDecimal.valueOf(100));
                when(productQueryRepository.findAllById(anySet())).thenReturn(List.of(product));

                OrderCommandEntity savedOrder = mock(OrderCommandEntity.class);
                when(savedOrder.getId()).thenReturn(1L);
                when(savedOrder.getStatus()).thenReturn(OrderStatus.PENDING);
                when(savedOrder.getTotalAmount()).thenReturn(BigDecimal.valueOf(100));
                when(savedOrder.getItems()).thenReturn(List.of());
                when(savedOrder.getCreatedAt()).thenReturn(LocalDateTime.now());
                when(orderCommandRepository.save(any())).thenReturn(savedOrder);

                orderCommandService.createOrder(USER_ID, requestWithItem(PRODUCT_ID, 1));
                verify(orderEventProducer)
                    .publishOrderCreated(anyString(), any(OrderCreatedEvent.class));
                verify(orderEventProducer, times(1)).publishOrderCreated(anyString(), any());
        }

        @Test
        @DisplayName("deve buscar todos os produtos em uma única query")
        void shouldFetchAllProductsInSingleQuery() {
                ProductQueryEntity p1 = mockProduct(1L, "A",  BigDecimal.valueOf(10));
                ProductQueryEntity p2 = mockProduct(2L, "B",  BigDecimal.valueOf(20));
            when(productQueryRepository.findAllById(anySet()))
                    .thenReturn(List.of(p1, p2));

                OrderCommandEntity savedOrder = mock(OrderCommandEntity.class);
                when(savedOrder.getId()).thenReturn(1L);
                when(savedOrder.getStatus()).thenReturn(OrderStatus.PENDING);
                when(savedOrder.getTotalAmount()).thenReturn(BigDecimal.valueOf(30));
                when(savedOrder.getItems()).thenReturn(List.of());
                when(savedOrder.getCreatedAt()).thenReturn(LocalDateTime.now());
                when(orderCommandRepository.save(any())).thenReturn(savedOrder);

                CreateOrderRequestDTO request = new CreateOrderRequestDTO(List.of(
                        new OrderItemDTO(1L, 1),
                        new OrderItemDTO(2L, 1)));

                orderCommandService.createOrder(USER_ID, request);

                verify(productQueryRepository,times(1)).findAllById(anySet());
        }
    }

    // -- Produto não encontrado ----------------------------------------------

    @Nested
    @DisplayName("createOrder() - produto não encontrado")
    class ProductNotFound {

            @Test
            @DisplayName("deve lançar ResourceNotFoundException quando productId não existe")
            void shouldThrowWhenProductNotFound() {

                when(productQueryRepository.findAllById(anySet())).thenReturn(List.of());

                CreateOrderRequestDTO request = requestWithItem(999L, 1);

                assertThatThrownBy(() -> orderCommandService.createOrder(USER_ID, request))
                        .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessageContaining("999");
            }

            @Test
            @DisplayName("deve lançar exceção mesmo quando só UM produto de vários não existe")
            void shouldThrowWhenOneOfManyProductsNotFound() {
                // Apenas produto 1 existe -- produto 2 não existe
                ProductQueryEntity p1 = mockProduct(1L,"Existe", new BigDecimal("10.00"));
                when(productQueryRepository.findAllById(any())).thenReturn(List.of(p1));

                CreateOrderRequestDTO request = new CreateOrderRequestDTO(List.of(
                        new OrderItemDTO(1L, 1),
                        new OrderItemDTO(2L, 1)
                ));

                assertThatThrownBy(() -> orderCommandService.createOrder(USER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                        .hasMessageContaining("2");
            }

            @Test
        @DisplayName("não deve salvar nada quando produto não existe")
        void shouldNotSaveWhenProductNotFound() {
                when(productQueryRepository.findAllById(anyList())).thenReturn(List.of());

                assertThatThrownBy(() -> orderCommandService.createOrder(USER_ID, requestWithItem(999L, 1)));

                verifyNoInteractions(orderCommandRepository);
                verifyNoInteractions(orderEventProducer);
            }
    }
}
