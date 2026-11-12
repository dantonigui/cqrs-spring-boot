package com.project.cqrs.command.webhook;
import com.project.cqrs.command.webhook.service.WebhookSignatureValidator;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class WebhookSignatureValidatorTest {
    WebhookSignatureValidator validator;
    @BeforeEach void setup() { validator = new WebhookSignatureValidator(); ReflectionTestUtils.setField(validator, "webhookSecret", "test-secret"); }
    String signature(String id, String request) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "ts=123,v1=" + HexFormat.of().formatHex(mac.doFinal(("id:"+id+";request-id:"+request+";ts:123;").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void acceptsValidSignatureAndNormalizesAlphanumericId() throws Exception { assertThat(validator.validate("ABC", signature("abc", "req"), "req")).isTrue(); }
    @Test void rejectsTamperedId() throws Exception { assertThat(validator.validate("456", signature("123", "req"), "req")).isFalse(); }
    @Test void rejectsWrongRequestId() throws Exception { assertThat(validator.validate("123", signature("123", "req"), "other")).isFalse(); }
    @Test void rejectsMissingSignature() { assertThat(validator.validate("123", null, "req")).isFalse(); }
    @Test void rejectsMalformedHex() { assertThat(validator.validate("123", "ts=123,v1=ZZZ", "req")).isFalse(); }
    @Test void missingSecretNeverDisablesProtection() { ReflectionTestUtils.setField(validator, "webhookSecret", ""); assertThat(validator.validate("123", "anything", "req")).isFalse(); }
}