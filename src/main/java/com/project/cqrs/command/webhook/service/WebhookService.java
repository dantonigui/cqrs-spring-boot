package com.project.cqrs.command.webhook.service;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
@Service
public class WebhookService {
    private final WebhookSignatureValidator validator;
    private final MercadoPagoWebhookProcessor processor;
    public WebhookService(WebhookSignatureValidator validator, MercadoPagoWebhookProcessor processor) { this.validator = validator; this.processor = processor; }
    public void processMercadopagoWebhook(Map<String, Object> body, String signature, String requestId, String dataId) {
        if (!validator.validate(dataId, signature, requestId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura inválida");
        if (!"payment".equals(body.get("type"))) return;
        if (!(body.get("data") instanceof Map<?, ?> data) || data.get("id") == null || !dataId.equalsIgnoreCase(data.get("id").toString())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do pagamento inconsistente");
        }
        processor.processPayment(dataId);
    }
}