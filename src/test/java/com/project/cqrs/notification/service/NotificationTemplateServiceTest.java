package com.project.cqrs.notification.service;

import com.project.cqrs.notification.dto.EmailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationTemplateService")
class NotificationTemplateServiceTest {

    private final NotificationTemplateService service =
            new NotificationTemplateService();

    @Nested
    @DisplayName("paymentApproved()")
    class PaymentApproved {

        @Test
        @DisplayName("deve incluir o número do pedido no assunto")
        void shouldIncludeOrderIdInSubject() {
            EmailMessage email = service.paymentApproved(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("199.90"), "PIX");

            assertThat(email.subject()).contains("42");
        }

        @Test
        @DisplayName("deve destinar ao e-mail correto")
        void shouldAddressCorrectRecipient() {
            EmailMessage email = service.paymentApproved(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("199.90"), "PIX");

            assertThat(email.to()).isEqualTo("cliente@teste.com");
        }

        @Test
        @DisplayName("deve incluir o valor formatado no corpo do e-mail")
        void shouldIncludeAmountInBody() {
            EmailMessage email = service.paymentApproved(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("199.90"), "PIX");

            assertThat(email.htmlBody()).contains("199.90");
        }

        @Test
        @DisplayName("deve traduzir o método de pagamento para exibição amigável")
        void shouldTranslatePaymentMethod() {
            EmailMessage email = service.paymentApproved(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("199.90"), "CREDIT_CARD");

            assertThat(email.htmlBody()).contains("Cartão de crédito");
        }

        @Test
        @DisplayName("deve incluir o nome do usuário na saudação")
        void shouldIncludeUserNameInGreeting() {
            EmailMessage email = service.paymentApproved(
                    "cliente@teste.com", "Ana Paula", 42L,
                    new BigDecimal("50.00"), "PIX");

            assertThat(email.htmlBody()).contains("Ana Paula");
        }
    }

    @Nested
    @DisplayName("orderCancelled()")
    class OrderCancelled {

        @Test
        @DisplayName("deve incluir aviso de estorno quando hadRefund=true")
        void shouldIncludeRefundNoticeWhenHadRefund() {
            EmailMessage email = service.orderCancelled(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("100.00"), "Desistência", true);

            assertThat(email.htmlBody()).contains("estorno");
        }

        @Test
        @DisplayName("não deve incluir aviso de estorno quando hadRefund=false")
        void shouldNotIncludeRefundNoticeWhenNoRefund() {
            EmailMessage email = service.orderCancelled(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("100.00"), "Nunca foi pago", false);

            assertThat(email.htmlBody()).doesNotContain("estorno");
        }

        @Test
        @DisplayName("deve incluir o motivo do cancelamento no corpo")
        void shouldIncludeReasonInBody() {
            EmailMessage email = service.orderCancelled(
                    "cliente@teste.com", "João", 42L,
                    new BigDecimal("100.00"), "Produto fora de estoque", false);

            assertThat(email.htmlBody()).contains("Produto fora de estoque");
        }

        @Test
        @DisplayName("deve incluir o número do pedido no assunto")
        void shouldIncludeOrderIdInSubject() {
            EmailMessage email = service.orderCancelled(
                    "cliente@teste.com", "João", 99L,
                    new BigDecimal("100.00"), "Motivo qualquer", false);

            assertThat(email.subject()).contains("99");
        }
    }
}