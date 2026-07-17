package com.project.cqrs.command.webhook.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.command.payment.service.PaymentApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MercadoPagoWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookProcessor.class);

    private final PaymentCommandRepository paymentCommandRepository;
    private final PaymentApprovalService paymentApprovalService;

    public MercadoPagoWebhookProcessor (PaymentCommandRepository paymentCommandRepository, PaymentApprovalService paymentApprovalService) {
        this.paymentCommandRepository = paymentCommandRepository;
        this.paymentApprovalService = paymentApprovalService;
    }

    @Transactional
    public void processPayment(String mpPaymentId) {

        try {
            Payment mp = new PaymentClient().get(Long.parseLong(mpPaymentId));

            log.info("Webhoook processando: mpPaymentId={}, status={}", mpPaymentId, mp.getStatus());

            paymentCommandRepository.findByMpPaymentId(mpPaymentId)
                    .ifPresent(payment -> {
                        if (payment.getPaymentStatus() == PaymentStatus.APPROVED || payment.getPaymentStatus() == PaymentStatus.REFUNDED) {
                            log.info("Pagamento {} já processado.", mpPaymentId);

                            return;
                        }

                        PaymentStatus paymentStatus = mapMpStatus(mp.getStatus());

                        payment.setPaymentStatus(paymentStatus);

                        paymentCommandRepository.save(payment);

                        if (paymentStatus == PaymentStatus.APPROVED) {

                            paymentApprovalService.approve(payment, mpPaymentId);

                        }
                    });
        } catch (MPApiException e) {
            log.error("Erro MP {} {}", e.getStatusCode(), e.getApiResponse().getContent());
        } catch (MPException e) {
            log.error("Erro SDK {}", e.getMessage());
        }
    }

    private PaymentStatus mapMpStatus(String status) {
        return switch (status.toLowerCase()) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected" -> PaymentStatus.REJECTED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "in_process" -> PaymentStatus.IN_PROCESS;
            default -> PaymentStatus.PENDING;
        };
    }
}
