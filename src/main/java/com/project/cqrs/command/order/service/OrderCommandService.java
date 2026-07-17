package com.project.cqrs.command.order.service;

import com.project.cqrs.command.order.dto.OrderItemDTO;
import com.project.cqrs.command.order.dto.OrderResponseDTO;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.model.OrderItemCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.order.dto.CreateOrderRequestDTO;
import com.project.cqrs.query.product.model.ProductQueryEntity;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderCommandService.class);

    private final OrderCommandRepository orderCommandRepository;
    private final ProductQueryRepository  productQueryRepository;
    private final OrderEventProducer orderEventProducer;

    public OrderCommandService(OrderCommandRepository orderCommandRepository, ProductQueryRepository productQueryRepository, OrderEventProducer orderEventProducer) {
        this.orderCommandRepository = orderCommandRepository;
        this.productQueryRepository = productQueryRepository;
        this.orderEventProducer = orderEventProducer;
    }

    @Transactional
    public void createOrder(Long userId, CreateOrderRequestDTO dto) {

        //1. Extrai todos os productIds do request
        Set<Long> productIds = dto.items().stream()
                .map(OrderItemDTO::productId)
                .collect(Collectors.toSet());

        //2. Busca todos os produtos em UMA query
        Map<Long, ProductQueryEntity> productMap = productQueryRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(ProductQueryEntity::getProductId, p -> p));

        //3. Valida que todos os produtos existem
        for(Long id : productIds){
            if(!productMap.containsKey(id)){
                throw new ResourceNotFoundException("Product not found" + id);
            }
        }

        //4. Monta itens com preço e nome do BANCO - nunca do frontend
        List<OrderItemCommandEntity> items = dto.items().stream().map(itemDto -> {
            ProductQueryEntity product =  productMap.get(itemDto.productId());

            LOG.debug("Item: productId={}, name={}, price={}, qty={}",
                    product.getProductId(),
                    product.getProductName(),
                    product.getProductPrice(),
                    itemDto.quantity());

            return OrderItemCommandEntity.of(product.getProductId(), product.getProductName(), product.getProductPrice(), itemDto.quantity());
        }).toList();

        //5. Cria e persiste o pedido
        //O total é calculado internamente pela entidade com os preços do banco
        OrderCommandEntity order = OrderCommandEntity.create(userId, items);
        OrderCommandEntity savedOrder = orderCommandRepository.save(order);

        LOG.info("Order created with id={}, saved order={}", savedOrder.getId(), savedOrder.getTotalAmount(), items.size(), userId);

        List<OrderCreatedEvent.ItemDTO> itemDTOs = savedOrder.getItems().stream()
                .map(i -> new OrderCreatedEvent.ItemDTO(
                        i.getProductId(),
                        i.getProductName(),
                        i.getUnitPrice(),
                        i.getQuantity()
        )).toList();

        OrderCreatedEvent event = OrderCreatedEvent.of(
                savedOrder.getId(),
                userId,
                savedOrder.getStatus(),
                savedOrder.getTotalAmount(),
                savedOrder.getCreatedAt(),
                itemDTOs
        );

        orderEventProducer.publishOrderCreated(savedOrder.getId().toString(), event);

        LOG.info("Pedido criado e evento publicado: orderId={}", savedOrder.getId());
    }
}
