package com.project.cqrs.query.order.consumer;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.order.kafka.consumer.OrderEventConsumer;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
import com.project.cqrs.shared.kafka.topics.OrderTopics;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer")
class OrderEventConsumerTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private IdempotencyService idempotencyService;

    private OrderEventConsumer consumer;

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final Integer DELIVERY_ATTEMPT = 1;

    @BeforeEach
    void setUp() {

        consumer = new OrderEventConsumer(
                orderQueryRepository,
                orderQueryService,
                idempotencyService
        );
    }

    private OrderCreatedEvent buildCreatedEvent() {

        return OrderCreatedEvent.of(
                ORDER_ID,
                USER_ID,
                OrderStatus.PENDING,
                new BigDecimal("100.00"),
                LocalDateTime.now(),
                List.of(
                        new OrderCreatedEvent.ItemDTO(
                                10L,
                                "Produto X",
                                new BigDecimal("100.00"),
                                1
                        )
                )
        );
    }

    private ProcessedEventEntity processed(String topic) {

        return ProcessedEventEntity.claim(
                "event-123",
                topic
        );
    }

    // ---------------------------------------------------------------------
    // onOrderCreated()
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("onOrderCreated()")
    class OnOrderCreated {

        @Test
        @DisplayName("Should ignore duplicated event")
        void shouldIgnoreDuplicatedEvent() {

            OrderCreatedEvent event = buildCreatedEvent();

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_CREATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(null);

            consumer.onOrderCreated(event, DELIVERY_ATTEMPT);

            verifyNoInteractions(orderQueryRepository);
            verifyNoInteractions(orderQueryService);
        }

        @Test
        @DisplayName("Should ignore when projection already exists")
        void shouldIgnoreWhenProjectionAlreadyExists() {

            OrderCreatedEvent event = buildCreatedEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_CREATED);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_CREATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.existsByOrderId(ORDER_ID))
                    .thenReturn(true);

            consumer.onOrderCreated(event, DELIVERY_ATTEMPT);

            verify(orderQueryRepository, never())
                    .save(any());

            verify(idempotencyService)
                    .markCompleted(processed);
        }

        @Test
        @DisplayName("Should create projection successfully")
        void shouldCreateProjectionSuccessfully() {

            OrderCreatedEvent event = buildCreatedEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_CREATED);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_CREATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.existsByOrderId(ORDER_ID))
                    .thenReturn(false);

            consumer.onOrderCreated(event, DELIVERY_ATTEMPT);

            verify(orderQueryRepository)
                    .save(any(OrderQueryEntity.class));

            verify(orderQueryService)
                    .evictUserOrdersCache();

            verify(idempotencyService)
                    .markCompleted(processed);

            verify(idempotencyService, never())
                    .markFailed(any());
        }

        @Test
        @DisplayName("Should mark event as failed when save throws exception")
        void shouldMarkFailedWhenSaveThrowsException() {

            OrderCreatedEvent event = buildCreatedEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_CREATED);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_CREATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.existsByOrderId(ORDER_ID))
                    .thenReturn(false);

            when(orderQueryRepository.save(any()))
                    .thenThrow(new RuntimeException("Database error"));

            assertThrows(
                    RuntimeException.class,
                    () -> consumer.onOrderCreated(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }

        @Test
        @DisplayName("Should mark event as failed when cache eviction fails")
        void shouldMarkFailedWhenCacheEvictionFails() {

            OrderCreatedEvent event = buildCreatedEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_CREATED);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_CREATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.existsByOrderId(ORDER_ID))
                    .thenReturn(false);

            doThrow(new RuntimeException("Redis unavailable"))
                    .when(orderQueryService)
                    .evictUserOrdersCache();

            assertThrows(
                    RuntimeException.class,
                    () -> consumer.onOrderCreated(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);
        }
    }

    // ---------------------------------------------------------------------
// onOrderStatusChanged()
// ---------------------------------------------------------------------

    @Nested
    @DisplayName("onOrderStatusChanged()")
    class OnOrderStatusChanged {

        private OrderStatusChangedEvent buildEvent() {

            return OrderStatusChangedEvent.of(
                    ORDER_ID,
                    USER_ID,
                    OrderStatus.PENDING,
                    OrderStatus.AWAITING_PAYMENT
            );
        }

        @Test
        @DisplayName("Should ignore duplicated event")
        void shouldIgnoreDuplicatedEvent() {

            OrderStatusChangedEvent event = buildEvent();

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_UPDATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(null);

            consumer.onOrderStatusChanged(
                    event,
                    DELIVERY_ATTEMPT
            );

            verifyNoInteractions(orderQueryRepository);
        }

        @Test
        @DisplayName("Should update order status successfully")
        void shouldUpdateOrderStatusSuccessfully() {

            OrderStatusChangedEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_UPDATED);

            OrderQueryEntity order = mock(OrderQueryEntity.class);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_UPDATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(order));

            consumer.onOrderStatusChanged(
                    event,
                    DELIVERY_ATTEMPT
            );

            verify(order)
                    .updateStatus(OrderStatus.AWAITING_PAYMENT);

            verify(orderQueryRepository)
                    .save(order);

            verify(orderQueryService)
                    .evictOrderCache(ORDER_ID);

            verify(orderQueryService)
                    .evictUserOrdersCache();

            verify(idempotencyService)
                    .markCompleted(processed);

            verify(idempotencyService, never())
                    .markFailed(any());
        }

        @Test
        @DisplayName("Should throw when projection does not exist")
        void shouldThrowWhenProjectionDoesNotExist() {

            OrderStatusChangedEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_UPDATED);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_UPDATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(
                    IllegalStateException.class,
                    () -> consumer.onOrderStatusChanged(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }

        @Test
        @DisplayName("Should mark failed when save throws exception")
        void shouldMarkFailedWhenSaveThrowsException() {

            OrderStatusChangedEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_UPDATED);

            OrderQueryEntity order = mock(OrderQueryEntity.class);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_UPDATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(order));

            when(orderQueryRepository.save(order))
                    .thenThrow(new RuntimeException("Database error"));

            assertThrows(
                    RuntimeException.class,
                    () -> consumer.onOrderStatusChanged(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }

        @Test
        @DisplayName("Should mark failed when cache eviction throws exception")
        void shouldMarkFailedWhenCacheEvictionThrowsException() {

            OrderStatusChangedEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_UPDATED);

            OrderQueryEntity order = mock(OrderQueryEntity.class);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_UPDATED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(order));

            doThrow(new RuntimeException("Redis unavailable"))
                    .when(orderQueryService)
                    .evictOrderCache(ORDER_ID);

            assertThrows(
                    RuntimeException.class,
                    () -> consumer.onOrderStatusChanged(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }
    }

    // ---------------------------------------------------------------------
// onOrderCancelled()
// ---------------------------------------------------------------------

    @Nested
    @DisplayName("onOrderCancelled()")
    class OnOrderCancelled {

        private OrderCancelledEvent buildEvent() {

            return OrderCancelledEvent.of(
                    ORDER_ID,
                    USER_ID,
                    "payment-123",
                    new BigDecimal("100.00"),
                    "Customer cancelled order"
            );
        }

        @Test
        @DisplayName("Should ignore duplicated event")
        void shouldIgnoreDuplicatedEvent() {

            OrderCancelledEvent event = buildEvent();

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_DELETED,
                    DELIVERY_ATTEMPT
            )).thenReturn(null);

            consumer.onOrderCancelled(
                    event,
                    DELIVERY_ATTEMPT
            );

            verifyNoInteractions(orderQueryRepository);
        }

        @Test
        @DisplayName("Should cancel order successfully")
        void shouldCancelOrderSuccessfully() {

            OrderCancelledEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_DELETED);

            OrderQueryEntity order = mock(OrderQueryEntity.class);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_DELETED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(order));

            consumer.onOrderCancelled(
                    event,
                    DELIVERY_ATTEMPT
            );

            verify(order)
                    .updateStatus(OrderStatus.CANCELLED);

            verify(orderQueryRepository)
                    .save(order);

            verify(orderQueryService)
                    .evictOrderCache(ORDER_ID);

            verify(orderQueryService)
                    .evictUserOrdersCache();

            verify(idempotencyService)
                    .markCompleted(processed);

            verify(idempotencyService, never())
                    .markFailed(any());
        }

        @Test
        @DisplayName("Should throw when projection does not exist")
        void shouldThrowWhenProjectionDoesNotExist() {

            OrderCancelledEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_DELETED);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_DELETED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.empty());

            assertThrows(
                    IllegalStateException.class,
                    () -> consumer.onOrderCancelled(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }

        @Test
        @DisplayName("Should mark failed when save throws exception")
        void shouldMarkFailedWhenSaveThrowsException() {

            OrderCancelledEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_DELETED);

            OrderQueryEntity order = mock(OrderQueryEntity.class);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_DELETED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(order));

            when(orderQueryRepository.save(order))
                    .thenThrow(new RuntimeException("Database error"));

            assertThrows(
                    RuntimeException.class,
                    () -> consumer.onOrderCancelled(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }

        @Test
        @DisplayName("Should mark failed when cache eviction throws exception")
        void shouldMarkFailedWhenCacheEvictionThrowsException() {

            OrderCancelledEvent event = buildEvent();

            ProcessedEventEntity processed =
                    processed(OrderTopics.ORDER_DELETED);

            OrderQueryEntity order = mock(OrderQueryEntity.class);

            when(idempotencyService.tryClaim(
                    event.eventId(),
                    OrderTopics.ORDER_DELETED,
                    DELIVERY_ATTEMPT
            )).thenReturn(processed);

            when(orderQueryRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(order));

            doThrow(new RuntimeException("Redis unavailable"))
                    .when(orderQueryService)
                    .evictUserOrdersCache();

            assertThrows(
                    RuntimeException.class,
                    () -> consumer.onOrderCancelled(
                            event,
                            DELIVERY_ATTEMPT
                    )
            );

            verify(idempotencyService)
                    .markFailed(processed);

            verify(idempotencyService, never())
                    .markCompleted(processed);
        }
    }
}