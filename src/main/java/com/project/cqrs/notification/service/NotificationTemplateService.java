package com.project.cqrs.notification.service;
import com.project.cqrs.notification.dto.EmailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.math.BigDecimal;
@Service
public class NotificationTemplateService {
    private String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8"); }
    public EmailMessage paymentApproved(String to, String name, Long orderId, BigDecimal amount, String method) {
        String label = switch (method) { case "CREDIT_CARD" -> "Cartão de crédito"; case "DEBIT_CARD" -> "Cartão de débito"; case "CASH" -> "Dinheiro"; default -> method; };
        return new EmailMessage(to, "Pagamento confirmado — pedido #" + orderId,
                "<p>Olá, " + escape(name) + "!</p><p>Pagamento de " + amount.toPlainString() + " confirmado por " + escape(label) + " para o pedido #" + orderId + ".</p>");
    }
    public EmailMessage orderCancelled(String to, String name, Long orderId, BigDecimal amount, String reason, boolean hadRefund) {
        return new EmailMessage(to, "Pedido #" + orderId + " cancelado",
                "<p>Olá, " + escape(name) + "!</p><p>Pedido cancelado: " + escape(reason) + ". Valor: " + amount.toPlainString() + ".</p>" + (hadRefund ? "<p>O estorno foi solicitado ao provedor.</p>" : ""));
    }
}