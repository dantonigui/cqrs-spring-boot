package com.project.cqrs.command.webhook.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebhookService {

    private final WebhookSignatureValidator validator;
    private final MercadoPagoWebhookProcessor processor;

    public WebhookService(WebhookSignatureValidator validator, MercadoPagoWebhookProcessor processor) {
        this.validator = validator;
        this.processor = processor;
    }

    public void processMercadopagoWebhook(
            Map<String, Object> body,
            String signature,
            String requestId
    ) {
        if (!validator.validate(body, signature, requestId)) {
            return;
        }

        if (!"payment".equals(body.get("type"))) {
            return;
        }

        Map<String, Object> data = (Map<String, Object>) body.get("data");

        if (data == null || data.get("id") == null) {
            return;
        }

        processor.processPayment(data.get("id").toString());
    }
}
