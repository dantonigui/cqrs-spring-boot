package com.project.cqrs.notification.service;
import com.project.cqrs.notification.dto.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
@Service
public class SmtpEmailSenderService implements EmailSenderService {
    private final JavaMailSender mail;
    private final String from;
    public SmtpEmailSenderService(JavaMailSender mail, @Value("${app.mail.from}") String from) { this.mail = mail; this.from = from; }
    public void send(EmailMessage message) {
        try {
            var mime = mail.createMimeMessage(); var helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(from); helper.setTo(message.to()); helper.setSubject(message.subject()); helper.setText(message.htmlBody(), true);
            mail.send(mime);
        } catch (Exception e) { throw new IllegalStateException("Falha ao enviar notificação", e); }
    }
}