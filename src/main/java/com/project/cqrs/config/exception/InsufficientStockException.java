package com.project.cqrs.config.exception;
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long id) { super("Estoque insuficiente para o produto " + id); }
}