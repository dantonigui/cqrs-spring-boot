package com.project.cqrs.notification.dto;
public record EmailMessage(String to, String subject, String htmlBody) {}