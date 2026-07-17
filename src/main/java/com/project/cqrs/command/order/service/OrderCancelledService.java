package com.project.cqrs.command.order.service;

import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.payment.PaymentRefundCreateRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Orquestra o cancelamento de um pedido.
 *
 * Regras de negócio:
 *   - Pedido PAID     → estorna o pagamento no Mercado Pago antes de cancelar
 *   - Pedido PENDING / AWAITING_PAYMENT → cancela sem estorno
 *   - Pedido CANCELLED → rejeita (já cancelado)
 *
 * Após cancelar, publica OrderCancelledEvent no Kafka para o
 * Query Side atualizar a projeção de leitura.
 */
@Service
public class OrderCancelledService {

    private static final Logger log =  LoggerFactory.getLogger(OrderCancelledService.class);

    private final OrderCommandRepository orderRepository;
    private final PaymentCommandRepository paymentRepository;
    private final OrderEventProducer orderEventProducer;

    public OrderCancelledService(
            OrderCommandRepository orderRepository,
            PaymentCommandRepository paymentRepository,
            OrderEventProducer orderEventProducer
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderEventProducer = orderEventProducer;
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId, String reason) {

        //Lock pessimista - evita cancelamento simultâneo
        OrderCommandEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado" + orderId));

        // Pedido já cancelado
        if(!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Pedido #" + orderId + "já estáa cancelado.");
        }

        String mpPaymentId = null;

        // Se o pedido está PAGO → tenta estornar no Mercado Pago
        if(order.getStatus() == OrderStatus.PAID) {
            mpPaymentId = refundPayment(orderId);
        }

        //Cancela o pagamento pendente no banco (se houver)
        paymentRepository.findAll().stream()
                .filter(p -> p.getOrder().getId().equals(orderId))
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PENDING || p.getPaymentStatus() == PaymentStatus.IN_PROCESS)
                .forEach(p -> {
                    p.setPaymentStatus(PaymentStatus.CANCELLED);
                    paymentRepository.save(p);
                });

        // Cancela o pedido
        order.markAsCancelled();
        orderRepository.save(order);

        log.info("Pedido cancelado: orderId={}, userId={}, reason={}", orderId, userId, reason);

        OrderCancelledEvent event = OrderCancelledEvent.of(
                orderId,
                userId,
                mpPaymentId,
                order.getTotalAmount(),
                reason
        );
        orderEventProducer.publishOrderCancelled(orderId.toString(), event);
    }

    /**
     * Solicita estorno total no Mercado Pago.
     *
     * @return mpPaymentId do pagamento estornado, ou null se não havia
     *         pagamento com ID no MP (ex: pagamento presencial)
     */

    private String refundPayment(Long orderId) {
        return paymentRepository.findAll().stream()
                .filter(p -> p.getOrder().getId().equals(orderId))
                .filter(p -> p.getPaymentStatus() == PaymentStatus.APPROVED)
                .filter(p -> p.getMpPaymentId() != null)
                .findFirst()
                .map(payment -> {
                    try {
                        // Estorno total - sem informar valor = estorno integral
                        new PaymentRefundClient().refund(Long.parseLong(payment.getMpPaymentId()));

                        payment.setPaymentStatus(PaymentStatus.REFUNDED);
                        paymentRepository.save(payment);

                        log.info("Estorno solicitando no MP: mpPaymentId ={}, orderId={}", payment.getMpPaymentId(), orderId);

                        return payment.getMpPaymentId();
                    } catch (MPApiException e) {
                        log.error("Erro na API do MP ao estornar: {} - {}",
                                e.getStatusCode(), e.getApiResponse().getContent());

                        throw new IllegalStateException(
                                "Falha ao estornar pagamento no Mercado Pago. " +
                                        "Tente novamente ou contate o suporte"
                        );
                    } catch (MPException e) {
                        log.error("Erro SDK ao estornar: {}", e.getMessage());
                        throw new IllegalStateException(
                                "Erro interno ao processar estorno.");
                    }
                }).orElse(null);
    }
}
