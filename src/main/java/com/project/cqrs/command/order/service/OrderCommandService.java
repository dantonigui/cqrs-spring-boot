package com.project.cqrs.command.order.service;

import com.project.cqrs.command.order.dto.OrderItemDTO;
import com.project.cqrs.command.order.dto.OrderResponseDTO;
import com.project.cqrs.command.order.model.OrderEntity;
import com.project.cqrs.command.order.model.OrderItemEntity;
import com.project.cqrs.command.order.repository.OrderRepository;
import com.project.cqrs.command.order.dto.CreateOrderRequestDTO;
import com.project.cqrs.query.product.model.ProductQueryEntity;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
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

    private final OrderRepository orderRepository;
    private final ProductQueryRepository  productQueryRepository;

    public OrderCommandService(OrderRepository orderRepository, ProductQueryRepository productQueryRepository) {
        this.orderRepository = orderRepository;
        this.productQueryRepository = productQueryRepository;
    }

    @Transactional
    public OrderResponseDTO createOrder(Long userId, CreateOrderRequestDTO dto) {

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
        List<OrderItemEntity> items = dto.items().stream().map(itemDto -> {
            ProductQueryEntity product =  productMap.get(itemDto.productId());

            LOG.debug("Item: productId={}, name={}, price={}, qty={}",
                    product.getProductId(),
                    product.getProductName(),
                    product.getProductPrice(),
                    itemDto.quantity());

            return OrderItemEntity.of(product.getProductId(), product.getProductName(), product.getProductPrice(), itemDto.quantity());
        }).toList();

        //5. Cria e persiste o pedido
        //O total é calculado internamente pela entidade com os preços do banco
        OrderEntity order = OrderEntity.create(userId, items);
        OrderEntity savedOrder = orderRepository.save(order);

        LOG.info("Order created with id={}, saved order={}", savedOrder.getId(), savedOrder.getTotalAmount(), items.size(), userId);

        List<OrderResponseDTO.ItemDTO> itemDTOs = savedOrder.getItems().stream()
                .map(i -> new OrderResponseDTO.ItemDTO(
                        i.getProductId(),
                        i.getProductName(),
                        i.getUnitPrice(),
                        i.getQuantity()
        )).toList();

        return new OrderResponseDTO(
                savedOrder.getId(),
                savedOrder.getStatus().name(),
                savedOrder.getTotalAmount(),
                itemDTOs);
    }
}
