package com.project.cqrs.command.product.service;
import com.project.cqrs.command.order.dto.OrderItemDTO;
import com.project.cqrs.command.order.model.OrderItemCommandEntity;
import com.project.cqrs.command.product.repository.ProductRepository;
import com.project.cqrs.command.product.kafka.producer.ProductEventProducer;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional("commandTransactionManager")
public class InventoryService {
    private final ProductRepository products;
    private final ProductEventProducer events;
    public InventoryService(ProductRepository products, ProductEventProducer events) { this.products = products; this.events = events; }
    public List<OrderItemCommandEntity> reserve(List<OrderItemDTO> items) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (var item : items) quantities.merge(item.productId(), item.quantity(), Math::addExact);
        List<OrderItemCommandEntity> result = new ArrayList<>();
        quantities.forEach((id, quantity) -> {
            var product = products.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
            product.reserveStock(quantity);
            result.add(OrderItemCommandEntity.of(id, product.getProductName(), product.getProductPrice(), quantity));
            events.sendProductUpdated(id.toString(), ProductUpdateEvent.fromEntity(product));
        });
        return result;
    }
    public void release(List<OrderItemCommandEntity> items) {
        Map<Long, Integer> quantities = new TreeMap<>();
        for (var item : items) quantities.merge(item.getProductId(), item.getQuantity(), Math::addExact);
        quantities.forEach((id, quantity) -> {
            var product = products.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
            product.replenishStock(quantity);
            events.sendProductUpdated(id.toString(), ProductUpdateEvent.fromEntity(product));
        });
    }
    public void replenish(Long id, int quantity) {
        var product = products.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + id));
        product.replenishStock(quantity);
        events.sendProductUpdated(id.toString(), ProductUpdateEvent.fromEntity(product));
    }
}