package com.project.cqrs.command.payment.model;

public enum PaymentStatus {
    PENDING, APPROVED, REJECTED, CANCELLED, REFUNDED, IN_PROCESS
}
