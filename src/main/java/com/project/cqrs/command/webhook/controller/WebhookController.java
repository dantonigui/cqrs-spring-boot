package com.project.cqrs.command.webhook.controller;

import com.project.cqrs.command.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("api/v1/webhooks")
@Tag(name = "Webhook", description = "Receber notificações do Mercado Pago")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receiveMercadoPagoWebhook(

            @RequestBody Map<String, Object> body,

            @RequestHeader(value = "x-signature", required = false)
            String signature,

            @RequestHeader(value = "x-request-id", required = false)
            String requestId
            ) {
        log.info("Webhook recebido: type={}, requestId={}", body.get("type"), requestId);

        webhookService.processMercadopagoWebhook(body, signature, requestId);

        return ResponseEntity.ok().build();
    }
}
