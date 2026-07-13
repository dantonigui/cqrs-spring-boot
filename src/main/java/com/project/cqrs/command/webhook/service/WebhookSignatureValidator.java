package com.project.cqrs.command.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

@Service
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);

    @Value("${mp.webhook-secret}")
    private String webhookSecret;

    public boolean validate(
            Map<String, Object> body,
            String signature,
            String requestId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true;
        }

        return isValidSignature(body,signature,requestId);
    }

    private boolean isValidSignature(
            Map<String, Object> body,
            String signature,
            String requestId
    ) {
        try {
            if (signature == null) return false;

            String ts = null;
            String v1 = null;

            for (String part : signature.split(",")) {
                String[] kv = part.split("=", 2);
                if ("ts".equals(kv[0])) ts = kv[1];
                if ("v1".equals(kv[0])) v1 = kv[1];
            }

            if (ts == null || v1 == null) return false;

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            String dataId = data != null ? data.get("id").toString() : "";

            String manifest = "id:" + dataId
                    + ";request-id:" + requestId
                    + ";ts:" + ts + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));

            String computed = HexFormat.of().formatHex(
                    mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

            return computed.equals(v1);

        } catch (Exception e) {
            log.error("Erro ao validar assinatura do webhook: {}",
                    e.getMessage());
            return false;
        }
    };


}
