package com.project.cqrs.query.order.consumer;

import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.order.kafka.consumer.OrderEventConsumer;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import com.project.cqrs.query.payment.repository.PaymentQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
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
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer")
public class OrderEventConsumerTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private PaymentQueryRepository paymentQueryRepository;

    @Mock
    private OrderQueryService  orderQueryService;

    @Mock
    private IdempotencyService  idempotencyService;

    private OrderEventConsumer orderEventConsumer;

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        orderEventConsumer = new OrderEventConsumer(
                orderQueryRepository, paymentQueryRepository, orderQueryService, idempotencyService
        );
    }

    // -- onOrderCreated ------------------------------------------------------

    @Nested
    @DisplayName("onOrderCreated()")
    class onOrderCreated {

        private OrderCreatedEvent buildEvent() {
            return OrderCreatedEvent.of(ORDER_ID, USER_ID, OrderStatus.PENDING, new BigDecimal("100.00"), LocalDateTime.now(),
                    List.of(new OrderCreatedEvent.ItemDTO(10L, "Produto X", new BigDecimal("100.00"),1)));
        }

        @Test
        @DisplayName("não deve processar quando idempotencyService retorna false")
        void shouldSkipWhenNotIdempotent() {
            OrderCreatedEvent event = buildEvent();
            when(idempotencyService.isNew(event.eventId(), "order.created")).thenReturn(false);

            orderEventConsumer.onOrderCreated(event);

            verifyNoInteractions(orderQueryRepository);
            verifyNoInteractions(orderQueryService);
        }

        @Test
        @DisplayName("não deve salvar quando o orderId já existe na projeção (guarda extra)")
        void shouldSkipWhenOrderAlreadyExistsInProjection() {
            OrderCreatedEvent event = buildEvent();
            when(idempotencyService.isNew(event.eventId(), "order.created")).thenReturn(true);
            when(orderQueryRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

            orderEventConsumer.onOrderCreated(event);

            verify(orderQueryRepository, never()).save(any());
        }

        @Test
        @DisplayName("deve salvar a projeção e invalidar o cache da lista quando é novo")
        void shouldSaveProjectionAndEvictCacheWhenNew() {
            OrderCreatedEvent event = buildEvent();
            when(idempotencyService.isNew(event.eventId(), "order.created")).thenReturn(true);
            when(orderQueryRepository.existsByOrderId(ORDER_ID)).thenReturn(false);

            orderEventConsumer.onOrderCreated(event);

            verify(orderQueryRepository).save(any(OrderQueryEntity.class));

            verify(orderQueryService).evictUserOrdersCache();
        }

        // -- onOrderStatusChanged --------------------------------------------

        @Nested
        @DisplayName("onOrderStatusChanged()")
        class onOrderStatusChanged {

            private OrderStatusChangedEvent buildEvent() {
                return OrderStatusChangedEvent.of(
                        ORDER_ID,USER_ID,OrderStatus.PENDING,OrderStatus.AWAITING_PAYMENT);
            }

            @Test
            @DisplayName("não deve processar quando idempotencyService retorna false")
            void shouldSkipWhenNotIdempotent() {

                OrderStatusChangedEvent event = buildEvent();
                when(idempotencyService.isNew(event.eventId(), "order.status.changed")).thenReturn(false);

                orderEventConsumer.onOrderStatusChanged(event);

                verifyNoInteractions(orderQueryRepository);
            }

            @Test
            @DisplayName("deve atualizar o status quando o pedido é encontrado na projeção")
            void shouldUpdateStatusWhenOrderFound() {

                OrderStatusChangedEvent event = buildEvent();
                OrderQueryEntity order = mock(OrderQueryEntity.class);

                when(idempotencyService.isNew(event.eventId(), "order.status.changed")).thenReturn(true);

                when(orderQueryRepository.findByOrderId(ORDER_ID))
                        .thenReturn(Optional.of(order));

                orderEventConsumer.onOrderStatusChanged(event);

                verify(order).updateStatus(OrderStatus.AWAITING_PAYMENT);
                verify(orderQueryRepository).save(order);
                verify(orderQueryService).evictOrderCache(ORDER_ID);
                verify(orderQueryService).evictUserOrdersCache();
            }

            @Test
            @DisplayName("não deve lançar exceção quando o pedido não existe na projeção")
            void shouldNotThrowWhenOrderNotFoundInProjection() {
                OrderStatusChangedEvent event = buildEvent();
                when(idempotencyService.isNew(event.eventId(), "order.status.changed"))
                        .thenReturn(true);
                when(orderQueryRepository.findByOrderId(ORDER_ID))
                        .thenReturn(Optional.empty());

                orderEventConsumer.onOrderStatusChanged(event);

                verify(orderQueryRepository, never()).save(any());
            }
        }

        // -- onOrderCancelled ------------------------------------------------

        @Nested
        @DisplayName("onOrderCancelled()")
        class onOrderCancelled {

            private OrderCancelledEvent buildEvent() {
                return OrderCancelledEvent.of(
                        ORDER_ID, USER_ID, "mp-123", new BigDecimal("100.00"), "Cliente desistiu");
            }

            @Test
            @DisplayName("não deve processar quando idempotencyService retorna false")
            void shouldSkipWhenNotIdempotent() {
                OrderCancelledEvent event = buildEvent();
                when(idempotencyService.isNew(event.eventId(), "order.cancelled"))
                        .thenReturn(false);

                orderEventConsumer.onOrderCancelled(event);

                verifyNoInteractions(orderQueryRepository);
            }

            @Test
            @DisplayName("deve marcar o pedido como CANCELLED na projeção")
            void shouldMarkOrderAsCancelledInProjection() {
                OrderCancelledEvent event = buildEvent();
                OrderQueryEntity order = mock(OrderQueryEntity.class);

                when(idempotencyService.isNew(event.eventId(), "order.cancelled"))
                        .thenReturn(true);
                when(orderQueryRepository.findByOrderId(ORDER_ID))
                        .thenReturn(Optional.of(order));

                orderEventConsumer.onOrderCancelled(event);

                verify(order).updateStatus(OrderStatus.CANCELLED);
                verify(orderQueryRepository).save(order);
            }
        }



    }


}
