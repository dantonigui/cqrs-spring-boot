package com.project.cqrs.admin.idempotency.entity;

public enum EventStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
