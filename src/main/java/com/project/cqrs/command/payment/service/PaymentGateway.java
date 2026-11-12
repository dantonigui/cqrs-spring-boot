package com.project.cqrs.command.payment.service;
import com.mercadopago.client.payment.*;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.*;
import com.mercadopago.resources.payment.Payment;
import com.project.cqrs.config.exception.PaymentException;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PaymentGateway {
    public Payment get(String id) {
        try { return new PaymentClient().get(Long.parseLong(id)); }
        catch (MPException | MPApiException e) { throw new PaymentException("Não foi possível consultar o pagamento. Tente novamente."); }
    }
    public void refund(String id) {
        try {
            var options = MPRequestOptions.builder().customHeaders(Map.of("X-Idempotency-Key", "refund-" + id)).build();
            new PaymentRefundClient().refund(Long.parseLong(id), options);
        } catch (MPException | MPApiException e) { throw new PaymentException("Não foi possível estornar o pagamento. Tente novamente."); }
    }
    public boolean cancelOrRefund(String id) {
        Payment payment = get(id);
        if ("refunded".equals(payment.getStatus())) return true;
        if ("approved".equals(payment.getStatus())) { refund(id); return true; }
        if ("cancelled".equals(payment.getStatus()) || "rejected".equals(payment.getStatus())) return false;
        try {
            new PaymentClient().cancel(Long.parseLong(id));
            return false;
        } catch (MPException | MPApiException e) { throw new PaymentException("Não foi possível cancelar o pagamento pendente. Tente novamente."); }
    }
}