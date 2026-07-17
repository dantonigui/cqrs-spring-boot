package com.project.cqrs.command.payment.service;

import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.*;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.dto.request.CardCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.request.InPersonCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.request.PixCheckoutRequestDTO;
import com.project.cqrs.command.payment.dto.response.CardPaymentResponseDTO;
import com.project.cqrs.command.payment.dto.response.InPersonPaymentResponseDTO;
import com.project.cqrs.command.payment.dto.response.PixPaymentResponseDTO;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.config.exception.PaymentException;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MercadoPagoPaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoPaymentService.class);

    private final OrderCommandRepository orderCommandRepository;
    private final PaymentCommandRepository paymentCommandRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public MercadoPagoPaymentService(OrderCommandRepository orderCommandRepository, PaymentCommandRepository paymentCommandRepository) {
            this.orderCommandRepository = orderCommandRepository;
            this.paymentCommandRepository = paymentCommandRepository;
    }

    @Transactional
    public PixPaymentResponseDTO createPixPayment(Long orderId, PixCheckoutRequestDTO pixCheckoutRequestDTO) {

        // GUARDA 1: lock pessimista - elimina race condition
        OrderCommandEntity orderCommandEntity = orderCommandRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // GUARDA 2: status da ordem
        if (orderCommandEntity.getStatus() == OrderStatus.PAID) {
            throw new PaymentException("Pedido #" + orderId + " já foi pago.");
        }

        if (orderCommandEntity.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentException("Pedido #" + orderId + " está cancelado.");
        }

        // GUARDA 3: PIX pendente existente - reenviar sem criar novo no MP
        // Se o cliente clicar em "Pagar com PIX" duas vezes,
        // retorna o mesmo QR code sem cobrar novamente
        if (orderCommandEntity.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            return paymentCommandRepository.findPendingPixByOrderId(orderId).map(existing -> {
                LOG.info("PIX pendente reenviado (sem nova chamada ao MP): " + "orderId={}, mpPaymentId={}", orderId, existing.getMpPaymentId());
                return new PixPaymentResponseDTO(
                        existing.getId(),
                        orderId,
                        existing.getPaymentStatus().name(),
                        existing.getPixQrCode(),
                        existing.getPixQrCodeBase64(),
                        existing.getPixExpiration().toString(),
                        orderCommandEntity.getTotalAmount()
                );
            }).orElseGet(() -> callMpAndCreatePix(orderCommandEntity, pixCheckoutRequestDTO));
        }
        return callMpAndCreatePix(orderCommandEntity, pixCheckoutRequestDTO);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PIX
    // ══════════════════════════════════════════════════════════════════════════

    private PixPaymentResponseDTO callMpAndCreatePix(OrderCommandEntity order, PixCheckoutRequestDTO pixCheckoutRequestDTO) {

        order.markAsAwaitingPayment();
        PaymentCommandEntity payment = PaymentCommandEntity.forPix(order);
        paymentCommandRepository.save(payment);

        try {
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                    .transactionAmount(order.getTotalAmount())
                    .paymentMethodId("pix")
                    .description("Pedido #" + order.getId())
                    .notificationUrl(baseUrl + "")
                    .externalReference(order.getId().toString())
                    .payer(PaymentPayerRequest.builder()
                            .email(pixCheckoutRequestDTO.payerEmail())
                            .firstName(pixCheckoutRequestDTO.payerFirstName())
                            .lastName(pixCheckoutRequestDTO.payerLastName())
                            .identification(IdentificationRequest.builder()
                                    .type("CPF")
                                    .number(pixCheckoutRequestDTO.payerDocument())
                                    .build()
                            ).build())
                    .build();

            Map<String, String> customHeaders = Map.of(
                    "X-Idempotency-Key",
                    "pix-order-" + order.getId()
            );

            MPRequestOptions options = MPRequestOptions.builder()
                    .customHeaders(customHeaders)
                    .build();

            Payment mp = new PaymentClient().create(request, options);

            payment.setMpPaymentId(mp.getId().toString());

            var txData = mp.getPointOfInteraction().getTransactionData();
            payment.setPixData(
                    txData.getQrCode(),
                    txData.getQrCodeBase64(),
                    LocalDateTime.now().plusMinutes(30)
            );

            paymentCommandRepository.save(payment);
            orderCommandRepository.save(order);

            LOG.info("PIX criado: orderId={}, mpPaymentId={}", order.getId(), payment.getId());

            return new PixPaymentResponseDTO(
                    payment.getId(),
                    order.getId(),
                    mp.getStatus(),
                    txData.getQrCode(),
                    txData.getQrCodeBase64(),
                    payment.getPixExpiration().toString(),
                    order.getTotalAmount()
            );

        } catch (MPApiException e) {
            LOG.error("MP API error ao criar PIX: {} - {}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            payment.reject();
            paymentCommandRepository.save(payment);
            throw new PaymentException(
                    "Falha ao criar PIX: " + e.getApiResponse().getContent());
        } catch (MPException e) {
            LOG.error("MP SDK error ao criar PIX: {}", e.getMessage());
            payment.reject();
            paymentCommandRepository.save(payment);
            throw new PaymentException("Erro interno ao processar PIX");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CARTÃO ONLINE
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public CardPaymentResponseDTO createCardPayment(Long orderId, CardCheckoutRequestDTO cardCheckoutRequestDTO) {

        // GUARDA 1: lock pessimista
        OrderCommandEntity order = orderCommandRepository.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // GUARDA 2: status da ordem
        if (order.getStatus() == OrderStatus.PAID) {
            throw new PaymentException("Pedido #" + orderId + " já foi pago.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentException("Pedido #" + orderId + " está cancelado.");
        }

        // GUARDA 3: pagamento ativo existente
        // Cartão não reenvia - PENDING pode estar em análise antifraude
        if (orderCommandRepository.hasActivePayment(orderId)) {
            throw new PaymentException(
                    "Pedido #" + orderId + " já possui pagamento em andamento. " +
                            "Aguarde a confirmação ou cancele antes de tentar novamente."
            );
        }

        order.markAsAwaitingPayment();
        PaymentCommandEntity payment = PaymentCommandEntity.forCard(order, cardCheckoutRequestDTO.installments());
        paymentCommandRepository.save(payment);

        try {
            // Monta itens para o additional_info
            // Melhora a taxa de aprovação do antifraude do MP
            List<PaymentItemRequest> items = order.getItems().stream()
                    .map(i -> PaymentItemRequest.builder()
                            .id(i.getProductId().toString())
                            .title(i.getProductName())
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .build()).toList();

            PaymentCreateRequest request = PaymentCreateRequest.builder()
                    .transactionAmount(order.getTotalAmount())
                    .token(cardCheckoutRequestDTO.cardToken())
                    .description("Pedido #" + orderId)
                    .installments(cardCheckoutRequestDTO.installments())
                    .paymentMethodId(cardCheckoutRequestDTO.paymentMethodId())
                    .notificationUrl(baseUrl + "")
                    .externalReference(orderId.toString())
                    .payer(PaymentPayerRequest.builder()
                            .email(cardCheckoutRequestDTO.payerEmail())
                            .identification(IdentificationRequest.builder()
                                    .type("CPF")
                                  .number(cardCheckoutRequestDTO.payerDocument())
                            .build())
                    .build())
                    .additionalInfo(PaymentAdditionalInfoRequest.builder()
                            .items(items)
                            .payer(PaymentAdditionalInfoPayerRequest.builder()
                                    .isFirstPurchaseOnline(false).build()).build()).build();

            // Idempotency por orderId + cardToken
            // mesmo token não gera dois pagamentos
            Map<String, String> customHeaders = Map.of(
                    "X-Idempotency-Key",
                    "card-" + orderId + "-" + cardCheckoutRequestDTO.cardToken()
            );

            MPRequestOptions options = MPRequestOptions.builder()
                    .customHeaders(customHeaders)
                    .build();

            Payment mp = new PaymentClient().create(request, options);

            payment.setMpPaymentId(mp.getId().toString());

            if (mp.getCard() != null) {
                payment.setCardData(mp.getCard().getLastFourDigits(),
                        cardCheckoutRequestDTO.paymentMethodId());
            }

            PaymentStatus status = mapMpStatus(mp.getStatus());
            payment.setPaymentStatus(status);

            if (status == PaymentStatus.APPROVED) {
                order.markAsPaid();
            }

            paymentCommandRepository.save(payment);
            orderCommandRepository.save(order);

            LOG.info("Cartão prcessado: orderId={}, mpPaymentId={}, status={}", orderId, mp.getId(), mp.getStatus());

            return new CardPaymentResponseDTO(
                    payment.getId(),
                    orderId,
                    mp.getStatus(),
                    mp.getStatusDetail(),
                    cardCheckoutRequestDTO.paymentMethodId(),
                    mp.getCard() != null ? mp.getCard().getLastFourDigits() : null,
                    cardCheckoutRequestDTO.installments(),
                    order.getTotalAmount()
            );

        } catch (MPApiException e) {
            LOG.error("MP API error ao processar cartão: {} - {}",
                    e.getStatusCode(), e.getApiResponse().getContent());
            payment.reject();
            paymentCommandRepository.save(payment);
            throw new PaymentException("Falha ao processar cartão: " + e.getApiResponse().getContent());
        } catch (MPException e) {
            LOG.error("MP SDK error ao processar cartão: {}", e.getMessage());
            payment.reject();
            paymentCommandRepository.save(payment);
            throw new PaymentException("Erro interno ao processar cartão");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PRESENCIAL
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public InPersonPaymentResponseDTO registerInPersonPayment(Long orderId, InPersonCheckoutRequestDTO dto) {

        // GUARDA 1: lock pessimista
        OrderCommandEntity order = orderCommandRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        // GUARDA 2: status da ordem
        if (order.getStatus() == OrderStatus.PAID) {
            throw new PaymentException("Pedido #" + orderId + " já foi pago.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentException("Pedido #" + orderId + " está cancelado.");
        }

        // GUARDA 3: pagamento ativo existente
        if (orderCommandRepository.hasActivePayment(orderId)) {
            throw new PaymentException("Pedido #" + orderId + " já possui pagamento registrado.");
        }

        PaymentCommandEntity payment = PaymentCommandEntity.forInPerson(order, dto.method());
        order.markAsPaid();

        paymentCommandRepository.save(payment);
        orderCommandRepository.save(order);

        LOG.info("Pagamento presencial registrado: orderId={}, method={}", orderId, dto.method());

        return new InPersonPaymentResponseDTO(
                payment.getId(),
                orderId,
                dto.method(),
                PaymentStatus.APPROVED.name(),
                order.getTotalAmount()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private PaymentStatus mapMpStatus(String mpStatus) {
        return switch (mpStatus.toLowerCase()) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            case "refudend" -> PaymentStatus.REFUNDED;
            case "in_process" -> PaymentStatus.IN_PROCESS;
            default -> PaymentStatus.PENDING;
        };
    }
}
