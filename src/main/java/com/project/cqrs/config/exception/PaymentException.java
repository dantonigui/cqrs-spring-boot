package com.project.cqrs.config.exception;

public class PaymentException extends RuntimeException{

    public PaymentException(String message) {
        super(message);
    }
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
