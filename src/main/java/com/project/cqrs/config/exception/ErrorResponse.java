package com.project.cqrs.config.exception;

import org.springframework.cglib.core.Local;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse (
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String,String> fieldErrors
) {
    static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                null
        );
    }

    static ErrorResponse ofValidation(String path, Map<String,String> fieldErrors) {
        return new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Um ou mais campos são inválidos",
                path,
                fieldErrors
        );
    }
}

