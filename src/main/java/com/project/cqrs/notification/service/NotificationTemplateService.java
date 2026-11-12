package com.project.cqrs.notification.service;

import com.project.cqrs.notification.dto.EmailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class NotificationTemplateService {
    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8");
    }

    private String currency(BigDecimal amount) {
        // Create per call: NumberFormat is mutable and not thread-safe.
        return escape(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(amount).replace(' ', ' '));
    }

    public EmailMessage paymentApproved(String to, String name, Long orderId, BigDecimal amount, String method) {
        String label = switch (method) {
            case "CREDIT_CARD" -> "Cartão de crédito";
            case "DEBIT_CARD" -> "Cartão de débito";
            case "CASH" -> "Dinheiro";
            case "PIX" -> "Pix";
            default -> method;
        };
        String details = row("Valor pago", currency(amount))
                + row("Forma de pagamento", escape(label));
        return new EmailMessage(to, "Pagamento confirmado — pedido #" + orderId,
                layout("Pagamento confirmado", "Recebemos seu pagamento.", name, orderId,
                        "Seu pagamento foi confirmado. Confira os detalhes abaixo.",
                        details, "", "#047857", "#ecfdf5"));
    }

    public EmailMessage orderCancelled(String to, String name, Long orderId, BigDecimal amount,
                                       String reason, boolean hadRefund) {
        String details = row("Valor do pedido", currency(amount))
                + row("Motivo do cancelamento", escape(reason));
        String notice = hadRefund
                ? """
                  <div style="margin-top:24px;padding:16px;background:#fffbeb;border:1px solid #fde68a;border-radius:8px;color:#92400e;font-size:14px;line-height:22px;">
                    <strong>Sobre o estorno</strong><br>
                    O estorno foi solicitado ao provedor. O prazo para o valor aparecer depende da instituição responsável pelo pagamento.
                  </div>
                  """
                : "";
        return new EmailMessage(to, "Pedido #" + orderId + " cancelado",
                layout("Pedido cancelado", "Atualização sobre seu pedido.", name, orderId,
                        "O cancelamento do seu pedido foi registrado. Confira os detalhes abaixo.",
                        details, notice, "#b45309", "#fffbeb"));
    }

    private String row(String label, String value) {
        return """
                <tr><td style="padding:14px 0;border-bottom:1px solid #e2e8f0;">
                  <div style="font-size:12px;line-height:18px;color:#64748b;margin-bottom:4px;">%s</div>
                  <div style="font-size:16px;line-height:24px;font-weight:600;color:#0f172a;overflow-wrap:anywhere;word-break:break-word;">%s</div>
                </td></tr>
                """.formatted(label, value);
    }

    private String layout(String title, String preheader, String name, Long orderId, String introduction,
                          String details, String notice, String accent, String tint) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>%s</title></head>
                <body style="margin:0;padding:0;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#334155;-webkit-text-size-adjust:100%%;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;mso-hide:all;">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f1f5f9;">
                    <tr><td align="center" style="padding:32px 12px;">
                      <!--[if mso]><table role="presentation" width="600" cellpadding="0" cellspacing="0"><tr><td><![endif]-->
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:12px;">
                        <tr><td style="padding:24px;background:#0f172a;border-radius:12px 12px 0 0;">
                          <div style="font-size:12px;line-height:18px;letter-spacing:2px;font-weight:bold;color:#cbd5e1;">SEU PEDIDO</div>
                          <div style="margin-top:8px;font-size:22px;line-height:30px;font-weight:bold;color:#ffffff;">Pedido #%s</div>
                        </td></tr>
                        <tr><td style="padding:28px 24px;">
                          <div style="display:inline-block;padding:6px 12px;background:%s;color:%s;border-radius:6px;font-size:12px;line-height:18px;font-weight:bold;">%s</div>
                          <h1 style="margin:20px 0 12px;font-size:26px;line-height:34px;color:#0f172a;">%s</h1>
                          <p style="margin:0 0 12px;font-size:16px;line-height:26px;">Olá, %s!</p>
                          <p style="margin:0 0 16px;font-size:15px;line-height:24px;">%s</p>
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">%s</table>
                          %s
                          <p style="margin:24px 0 0;font-size:14px;line-height:22px;color:#64748b;">Guarde esta mensagem para consultar os dados do seu pedido.</p>
                        </td></tr>
                        <tr><td style="padding:20px 24px;border-top:1px solid #e2e8f0;background:#f8fafc;border-radius:0 0 12px 12px;font-size:12px;line-height:20px;color:#64748b;">
                          Notificação automática sobre o pedido #%s.<br>Por favor, não responda a este e-mail.
                        </td></tr>
                      </table>
                      <!--[if mso]></td></tr></table><![endif]-->
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(escape(title), escape(preheader), orderId, tint, accent, escape(title),
                escape(title), escape(name), escape(introduction), details, notice, orderId);
    }
}