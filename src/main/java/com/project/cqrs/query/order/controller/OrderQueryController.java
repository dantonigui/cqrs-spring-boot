package com.project.cqrs.query.order.controller;

import com.project.cqrs.query.order.dto.OrderQueryResponseDTO;
import com.project.cqrs.query.order.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order Query", description = "Consulta de pedidos e pagamentos do usuário autenticado")
@RestController
@RequestMapping("/api/v1/query/orders")
public class OrderQueryController {

    private final OrderQueryService orderQueryService;

    public OrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @Operation(
            summary = "Lista pedidos do usuário",
            description = """
            Retorna a lista paginada de pedidos do usuário autenticado,
            ordenada do mais recente para o mais antigo.
 
            Resultado cacheado no Redis por 2 minutos.
            Retorna apenas o resumo — sem itens e pagamentos.
            Use GET /orders/{orderId} para o detalhe completo.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    @GetMapping
    public ResponseEntity<Page<OrderQueryResponseDTO>>findMyOrders(@AuthenticationPrincipal String userId,
                                                                   @ParameterObject @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(orderQueryService.findByUser(pageable, Long.parseLong(userId)));

    }

    @Operation(
            summary = "Detalhe de um pedido",
            description = """
            Retorna o detalhe completo de um pedido incluindo:
              - Todos os itens com preço unitário e subtotal
              - Todos os pagamentos associados (aprovados, rejeitados, etc.)
 
            Resultado cacheado no Redis por 5 minutos.
 
            Segurança: o usuário só pode visualizar seus próprios pedidos.
            Um orderId válido de outro usuário retorna 404 — não 403 —
            para não revelar a existência do pedido.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "404",
                    description = "Pedido não encontrado ou não pertence ao usuário")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderQueryResponseDTO> findOrderById(@Parameter(description = "ID do pedido") @PathVariable Long orderId,
                                                               @AuthenticationPrincipal String userId) {

        return ResponseEntity.ok(orderQueryService.findByOrderId(orderId, Long.parseLong(userId)));
    }
}
