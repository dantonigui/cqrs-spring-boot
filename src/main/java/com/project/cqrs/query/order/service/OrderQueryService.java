package com.project.cqrs.query.order.service;

import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.order.dto.OrderQueryResponseDTO;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryService.class);

    public static final String CACHE_ORDERS = "orders";
    public static final String CACHE_ORDER_DETAIL = "order_detail";

    private final OrderQueryRepository orderQueryRepository;

    public OrderQueryService(OrderQueryRepository orderQueryRepository) {
        this.orderQueryRepository = orderQueryRepository;
    }

    /**
     * Lista pedidos do usuário autenticado com paginação.
     *
     * Cache key: "orders::{userId}-page-{n}-size-{s}"
     * TTL: 2 minutos — pedidos mudam de status com frequência
     *
     * Retorna apenas o resumo (sem itens e pagamentos) para performance.
     * O detalhe é carregado sob demanda via findByOrderId.
     */
    @Cacheable(
            cacheNames = CACHE_ORDERS,
            key = "#userId + '-page' + #pageable.pageNumber "
            + "+ '-size' + #pageable.pageSize",
            unless = "#result == null || #result.isEmpty()"
    )
    @Transactional(readOnly = true)
    public Page<OrderQueryResponseDTO> findByUser(Pageable pageable, Long userId) {
        log.debug("Cache MISS - buscando pedidos do userId={} no MySQL", userId);
        return orderQueryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(OrderQueryResponseDTO::summary);
    }

    /**
     * Retorna o detalhe completo de um pedido incluindo itens e pagamentos.
     *
     * Cache key: "order-detail::{orderId}"
     * TTL: 5 minutos
     *
     * Validação de ownership: garante que o usuário só vê seus próprios pedidos.
     */
    @Cacheable(
            cacheNames = CACHE_ORDER_DETAIL,
            key = "#orderId",
            unless = "#result == null "
    )
    @Transactional(readOnly = true)
    public OrderQueryResponseDTO findByOrderId(Long orderId, Long userId) {
        log.debug("Cache MISS - buscando pedido orderId={} no MySQL", orderId);

        OrderQueryEntity entity = orderQueryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order Not Found" + orderId));

        //Validação de ownership: usuário só pode ver seus próprios pedidos
        if (!entity.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("User Not Found" + orderId);
            // Retorna 404 em vez de 403 para não revelar que o pedido existe
        }
        return OrderQueryResponseDTO.from(entity);
    }

    /**
     * Invalida o cache de um pedido específico.
     * Chamado pelo OrderEventConsumer após atualizar a projeção.
     */
    @CacheEvict(cacheNames = CACHE_ORDER_DETAIL, key = "#orderId")
    public void evictOrderCache(Long orderId) {
        log.debug("Cache evicto: order-detail::{}", orderId);
    }

    /**
     * Invalida o cache de lista de pedidos de um usuário.
     * Chamado pelo OrderEventConsumer após qualquer mudança de status.
     */
    @CacheEvict(cacheNames = CACHE_ORDERS, allEntries = true)
    public void evictUserOrdersCache() {
        log.debug("Cache evicto: orders-cache (allEntries");
    }
}
