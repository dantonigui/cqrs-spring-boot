package com.project.cqrs.command.order.model;

public enum OrderStatus {
    PENDING, AWAITING_PAYMENT, PAID, CANCELLED, REFUNDED
}
