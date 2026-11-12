package com.project.cqrs.notification.service;
import com.project.cqrs.notification.dto.EmailMessage;
public interface EmailSenderService { void send(EmailMessage message); }