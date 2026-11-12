package com.project.cqrs.command.webhook.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Service
public class WebhookSignatureValidator {
    @Value("${mp.webhook-secret}") private String webhookSecret;
    public boolean validate(Map<String, Object> body, String signature, String requestId) {
        Object data = body.get("data");
        return data instanceof Map<?, ?> map && map.get("id") != null && validate(map.get("id").toString(), signature, requestId);
    }
    public boolean validate(String dataId, String signature, String requestId) {
        if (webhookSecret == null || webhookSecret.isBlank() || signature == null || requestId == null || dataId == null) return false;
        try {
            Map<String, String> parts = new HashMap<>();
            for (String part : signature.split(",")) { String[] pair = part.trim().split("=", 2); if (pair.length != 2) return false; parts.put(pair[0], pair[1]); }
            String ts = parts.get("ts"), v1 = parts.get("v1");
            if (ts == null || v1 == null) return false;
            String manifest = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + requestId + ";ts:" + ts + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(v1));
        } catch (Exception e) { return false; }
    }
}