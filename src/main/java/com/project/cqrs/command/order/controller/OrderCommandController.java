package com.project.cqrs.command.order.controller;

import com.project.cqrs.command.order.dto.OrderResponseDTO;
import com.project.cqrs.command.order.service.OrderCommandService;
import com.project.cqrs.command.order.dto.CreateOrderRequestDTO;
import com.project.cqrs.command.payment.dto.request.CardCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.request.InPersonCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.request.PixCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.response.CardPaymentResponseDTO;
import com.project.cqrs.command.payment.dto.response.InPersonPaymentResponseDTO;
import com.project.cqrs.command.payment.dto.response.PixPaymentResponseDTO;
import com.project.cqrs.command.payment.service.MercadoPagoPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Order e Checkout",description = "Criação de pedidos e processamento de pagamentos")
@RestController
@RequestMapping("/api/v1/command/orders")
public class OrderCommandController {

    private final OrderCommandService orderCommandService;
    private final MercadoPagoPaymentService  paymentService;

    public OrderCommandController(OrderCommandService orderCommandService, MercadoPagoPaymentService paymentService) {
        this.orderCommandService = orderCommandService;
        this.paymentService = paymentService;
    }

    // ---------------- CRIAR PEDIDO -------------------
    @Operation(
            summary = "Cria um pedido",
            description = """
            Cria um novo pedido a partir dos itens do carrinho.
            
            O frontend envia apenas productId + quantity.
            Preço e nome do produto são buscados SEMPRE no banco pelo backend
            — nunca aceitos do request (proteção contra price tampering).
            
            O pedido é criado com status PENDING.
            O próximo passo é chamar um dos endpoints de checkout.
            """
    )
    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(@Valid @RequestBody CreateOrderRequestDTO dto,
                                                        @AuthenticationPrincipal String userId) {
        OrderResponseDTO order = orderCommandService.createOrder(Long.parseLong(userId), dto);
        return ResponseEntity.ok(order);
    }

    // ---------------- CHECKOUT CARTÃO ------------------

    @Operation(
            summary = "Checkout com cartão de crédito",
            description = """
            Processa o pagamento com cartão de crédito para o pedido informado.
            
            O campo cardToken deve ser gerado pelo MP.js no frontend
            a partir dos dados do cartão — o backend nunca recebe o número
            do cartão diretamente (PCI Compliance).
            
            O resultado é SÍNCRONO:
              - APPROVED   → pedido marcado como PAID imediatamente
              - IN_PROCESS → em análise antifraude (webhook confirma depois)
              - REJECTED   → pagamento recusado, tente outro cartão
            """
    )
    @PostMapping("/{orderId}/checkout/card")
    public ResponseEntity<CardPaymentResponseDTO> checkoutCard(@Parameter(description = "ID do pedido")
                                                               @PathVariable Long orderId, @Valid @RequestBody CardCheckoutRequestDTO dto) {
            return ResponseEntity.ok(paymentService.createCardPayment(orderId, dto));
    }

    // --------------- CHECKOUT PIX ------------------
    @Operation(
            summary = "Checkout com PIX",
            description = """
            Inicia o pagamento via PIX para o pedido informado.
            
            Retorna o QR code (texto e imagem base64) e o código copia-e-cola.
            O status do pedido muda para AWAITING_PAYMENT.
            
            O pagamento é confirmado de forma ASSÍNCRONA via webhook do
            Mercado Pago — o status PAID só é aplicado após a notificação.
            
            Se chamado duas vezes para o mesmo pedido, retorna o mesmo
            QR code sem criar novo pagamento no Mercado Pago.
            """
    )
    @PostMapping("/{orderId}/checkout/pix")
    public ResponseEntity<PixPaymentResponseDTO> checkoutPix(@Parameter(description = "ID do pedido")
                                                                 @PathVariable Long orderId, @Valid @RequestBody PixCheckoutRequestDTO dto) {
        return ResponseEntity.ok(paymentService.createPixPayment(orderId, dto));
    }

    // -------------- CHECKOUT PRESENCIAL ---------------
    @Operation(
            summary = "Registra pagamento presencial",
            description = """
            Registra um pagamento realizado presencialmente pelo operador de caixa.
            
            Métodos aceitos no campo method:
              - CASH   → dinheiro em espécie
              - CARD   → cartão na maquininha (débito ou crédito)
              - PIX    → PIX presencial (cliente mostra o comprovante)
            
            O pagamento é aprovado IMEDIATAMENTE — sem chamada à API do
            Mercado Pago. O operador confirma o recebimento fisicamente.
            
            O pedido é marcado como PAID na mesma requisição.
            """
    )
    @PostMapping("/{orderId}/checkout/in-person")
    public ResponseEntity<InPersonPaymentResponseDTO> checkoutInPerson(@PathVariable Long orderId,
                                                                       @Valid @RequestBody InPersonCheckoutRequestDTO dto) {
        return ResponseEntity.ok(paymentService.registerInPersonPayment(orderId, dto));
    }


}
