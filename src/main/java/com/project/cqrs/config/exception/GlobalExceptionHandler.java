package com.project.cqrs.config.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.tomcat.websocket.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleStock(InsufficientStockException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
    }
    // -- 404 --------------------------------------------------

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        ));
    }

    //-- 400 - Validações de DTOs (@Valid) ------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach((fieldError) -> {
            String field = ((FieldError) fieldError ).getField();
            String message = fieldError.getDefaultMessage();
            fieldErrors.put(field, message);
        });

        log.warn("Erro de validação em {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.ofValidation(
                request.getRequestURI(),
                fieldErrors
        ));
    }

    // - 400 - Tipo de parâmetro inválido (ex: /orders/abc em vez de /orders/1)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = String.format("Parâmetro '%s' deve ser do tipo %s",
                ex.getMessage(),
                ex.getRequiredType() != null
                    ? ex.getRequiredType().getSimpleName()
                    : "desconhecido");

        log.warn("Tipo de parâmetro inválido: {} - path: {}", message, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        HttpStatus.BAD_REQUEST,
                        message,
                        request.getRequestURI()
                ));
    }

    // -- 400 -- Regras de negócio (estado inválido) --------------------------

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Estado inválido: {} - path: {}", request.getRequestURI(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    // -- 422 -- Falhas de pagamento (Mercado Pago) ---------------------------

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException ex, HttpServletRequest request) {

        log.warn("Erro de pagamento: {} - path: {}", ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    // -- 401 -----------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        HttpStatus.UNAUTHORIZED,
                        "Não autenticado",
                        request.getRequestURI()
                ));
    }

    // -- 403 -----------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public  ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Acesso negado: {} - path: {}", request.getRequestURI(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(
                        HttpStatus.FORBIDDEN,
                        "Sem permissão para acessar este recurso",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(HttpStatus.CONFLICT,
                "Operação conflita com registros existentes", request.getRequestURI()));
    }

    // -- 500 - Fallback ------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {


        log.error("Erro não tratado: {} - path: {}", ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Tente novamente ou contate o suporte",
                request.getRequestURI() // Oq é esse requestURI, oq retorna?
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());

        // Log em nível diferente dependendo da severidade — 429 é
        // esperado em operação normal, não é um erro do servidor
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("Rate limit excedido: path={}", request.getRequestURI());
        } else {
            log.warn("ResponseStatusException: status={}, reason={}, path={}",
                    status, ex.getReason(), request.getRequestURI());
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);

        // Retry-After ajuda o cliente a saber quando tentar de novo
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            builder.header("Retry-After", "60"); // ajuste pro durationSeconds real
        }

        return builder.body(ErrorResponse.of(
                status,
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                request.getRequestURI()
        ));
    }
}