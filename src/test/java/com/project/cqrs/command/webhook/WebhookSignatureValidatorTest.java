package com.project.cqrs.command.webhook;


import com.project.cqrs.command.webhook.service.WebhookSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookSignatureValidator")
class WebhookSignatureValidatorTest {

    private static Logger logger = LoggerFactory.getLogger(WebhookSignatureValidatorTest.class);

    private WebhookSignatureValidator webhookSignatureValidator;

    private static final String webhookSecret = "${mp.webhook-secret}";
    private static final String REQUEST_ID = "req-123";
    private static final String DATA_ID = "456789";
    private static final String TIMESTAMP = "172000000000";

    @BeforeEach
    void setUp() {
        webhookSignatureValidator = new WebhookSignatureValidator();
        ReflectionTestUtils.setField(webhookSignatureValidator, "webhookSecret", webhookSecret);
    }

    // -- Helpers -------------------------------------------------------------

    private String buildSignature(String ts, String hash) {
        return "ts=" + ts + ",v1=" + hash;
    }

    private String computeHmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
        ));
        return HexFormat.of().formatHex(
                mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8))
        );
    }

    private Map<String, Object> buildBody(String dataId) {

        return Map.of(
                "type","payment",
                "data", Map.of("id", dataId)
        );
    }

    private String validManifest() {
        return "id:" + DATA_ID + ";request-id:" + REQUEST_ID
                + ";ts:" + TIMESTAMP + ";";
    }

    // -- Testes --------------------------------------------------------------

    @Nested
    @DisplayName("Assinatura válida")
    class ValidSignature {

        @Test
        @DisplayName("deve retornar true quando assinatura HMAC é correta")
        void shouldReturnTrueForValidSignature() throws Exception {
            String hash      = computeHmac(validManifest());
            String signature = buildSignature(TIMESTAMP, hash);
            Map<String, Object> body = buildBody(DATA_ID);

            assertThat(webhookSignatureValidator.validate(body,signature, REQUEST_ID)).isTrue();
            logger.info( "teste", body ,signature);
        }
    }

    @Nested
    @DisplayName("Assinatura inválida")
    class InvalidSignature {

        @Test
        @DisplayName("deve retornar false quando signature é null")
        void shouldReturnFalseWhenSignatureIsNull() {
            assertThat(webhookSignatureValidator.validate(buildBody(DATA_ID), null, REQUEST_ID)).isFalse();
        }

        @Test
        @DisplayName("deve retornar false quando hash está errado")
        void shouldReturnFalseWhenHashIsWrong() {
            String signature = buildSignature(TIMESTAMP, "hash-errado-qualquer");
            assertThat(webhookSignatureValidator.validate(buildBody(DATA_ID), signature, REQUEST_ID)).isFalse();
        }


        @Test
        @DisplayName("deve retornar false quando timestamp foi alterado")
        void shouldReturnFalseWhenTimestampAltered() throws Exception {

            String hash = computeHmac(validManifest());
            String signature = buildSignature("999999999999", hash);

            assertThat(webhookSignatureValidator.validate(buildBody(DATA_ID), signature, REQUEST_ID)).isFalse();
        }

        @Test
        @DisplayName("deve retornar false quando dataId foi alterado")
        void shouldReturnFalseWhenDataIdAltered() throws Exception {

            String hash = computeHmac(validManifest());
            String signature = buildSignature(TIMESTAMP, hash);

            Map<String, Object> tamperedBody = buildBody("id-diferente");

            assertThat(webhookSignatureValidator.validate(tamperedBody, signature, REQUEST_ID)).isFalse();
        }

        @Test
        @DisplayName("deve retornar false quando requestId foi alterado")
        void shouldReturnFalseWhenRequestIdAltered() throws Exception {

            String hash = computeHmac(validManifest());
            String signature = buildSignature(TIMESTAMP, hash);

            assertThat(webhookSignatureValidator.validate(buildBody(DATA_ID), signature, "req-outro")).isFalse();
        }

        @Test
        @DisplayName("deve retornar false quando header não tem ts ou v1")
        void shouldReturnFalseWhenHeaderMalformed() {
            assertThat(webhookSignatureValidator.validate(buildBody(DATA_ID), "apenas-string-aleatoria", REQUEST_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("Secret não configurado")
    class SecretNotConfigured {

        @Test
        @DisplayName("deve retornar true em dev quando secret está em branco")
        void shouldReturnTrueWhenSecretIsBlank(){
            ReflectionTestUtils.setField(webhookSignatureValidator, "webhookSecret", "");

            assertThat(webhookSignatureValidator.validate(buildBody(DATA_ID), null, REQUEST_ID)).isTrue();
        }
    }
}


