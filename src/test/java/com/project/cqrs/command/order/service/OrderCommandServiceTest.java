package com.project.cqrs.command.order.service;
import com.project.cqrs.command.order.dto.*;
import com.project.cqrs.command.order.model.*;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.product.service.InventoryService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class OrderCommandServiceTest {
    @Test void createsOrderWithReservedSnapshotAndReturnsId() {
        var orders = mock(OrderCommandRepository.class); var inventory = mock(InventoryService.class); var events = mock(OrderEventProducer.class);
        var dto = new CreateOrderRequestDTO(List.of(new OrderItemDTO(2L, 3)));
        when(inventory.reserve(dto.items())).thenReturn(List.of(OrderItemCommandEntity.of(2L, "Produto", new BigDecimal("10.00"), 3)));
        when(orders.save(any())).thenAnswer(i -> { var order = (OrderCommandEntity) i.getArgument(0); order.setOrderId(7L); return order; });
        assertThat(new OrderCommandService(orders, inventory, events).createOrder(1L, dto)).isEqualTo(7L);
        var captor = org.mockito.ArgumentCaptor.forClass(com.project.cqrs.shared.event.order.OrderCreatedEvent.class);
        verify(events).publishOrderCreated(eq("7"), captor.capture());
        assertThat(captor.getValue().totalAmount()).isEqualByComparingTo("30.00");
        assertThat(captor.getValue().status()).isEqualTo(com.project.cqrs.shared.enums.OrderStatus.PENDING);
        assertThat(captor.getValue().createdAt()).isNotNull();
    }
    @Test void cannotPersistOrPublishWhenReservationFails() {
        var orders = mock(OrderCommandRepository.class); var inventory = mock(InventoryService.class); var events = mock(OrderEventProducer.class);
        var dto = new CreateOrderRequestDTO(List.of(new OrderItemDTO(2L, 3)));
        when(inventory.reserve(any())).thenThrow(new com.project.cqrs.config.exception.InsufficientStockException(2L));
        assertThatThrownBy(() -> new OrderCommandService(orders, inventory, events).createOrder(1L, dto)).isInstanceOf(com.project.cqrs.config.exception.InsufficientStockException.class);
        verifyNoInteractions(orders, events);
    }
}