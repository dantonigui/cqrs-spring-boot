package com.project.cqrs.command.order.service;
import com.project.cqrs.command.order.dto.CreateOrderRequestDTO;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.product.service.InventoryService;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {
    private final OrderCommandRepository orders;
    private final InventoryService inventory;
    private final OrderEventProducer events;
    public OrderCommandService(OrderCommandRepository orders, InventoryService inventory, OrderEventProducer events) {
        this.orders = orders; this.inventory = inventory; this.events = events;
    }
    @Transactional("commandTransactionManager")
    public Long createOrder(Long userId, CreateOrderRequestDTO dto) {
        var order = orders.save(OrderCommandEntity.create(userId, inventory.reserve(dto.items())));
        var items = order.getItems().stream().map(i -> new OrderCreatedEvent.ItemDTO(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity())).toList();
        events.publishOrderCreated(order.getOrderId().toString(), OrderCreatedEvent.of(order.getOrderId(), userId, order.getStatus(), order.getTotalAmount(), order.getCreatedAt(), items));
        return order.getOrderId();
    }
}