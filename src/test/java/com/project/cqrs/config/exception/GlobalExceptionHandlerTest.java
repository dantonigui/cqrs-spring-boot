package com.project.cqrs.config.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler")
public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;
    private MockHttpServletRequest mockHttpServletRequest;

    @BeforeEach
    public void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
        mockHttpServletRequest = new MockHttpServletRequest("GET", "/api/v1/tests");
    }

    @Nested
    @DisplayName("ResourceNotFoundException -> 404")
    class NotFound {

        @Test
        @DisplayName("deve retornar 404 com a mensagem da exceção")
        void shouldReturn404() {
            var ex = new ResourceNotFoundException("Product Not Found: 42");

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNotFoundException(ex, mockHttpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().message()).isEqualTo("Product Not Found: 42");
            assertThat(response.getBody().path()).isEqualTo("/api/v1/tests");
            assertThat(response.getBody().fieldErrors()).isNull();
            assertThat(response.getBody().timestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException -> 400 com fieldErrors")
    class Validation {

        @Test
        @DisplayName("deve retornar 400 com mapa de error por campo")
        void shouldReturn400WithFieldErrors() {

            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
               new FieldError("dto", "productId", "não deve ser nulo"),
               new FieldError("dto", "quantity", "deve ser positivo")
            ));



            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMethodArgumentNotValidException(ex, mockHttpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().fieldErrors())
                    .containsEntry("productId", "não deve ser nulo")
                            .containsEntry("quantity", "deve ser positivo");

            assertThat(response.getBody().error()).isEqualTo("Validation Failed");
        }
    }

    @Nested
    @DisplayName("PaymentException -> 422")
    class Payment {

        @Test
        @DisplayName("deve retornar 422 para erros de pagamento")
        void shouldReturn422WithPayment() {

            var ex = new PaymentException("Pedido #1 já foi pago.");

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handlePaymentException(ex, mockHttpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().message()).isEqualTo("Pedido #1 já foi pago.");
        }
    }

    @Nested
    @DisplayName("IllegalStateException -> 400")
    class IllegalState {

        @Test
        @DisplayName("deve retornar 400 para estado inválido")
        void shouldReturn400() {
            var ex = new IllegalStateException("Pedido já cancelado.");

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalStateException(ex, mockHttpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().message()).isEqualTo("Pedido já cancelado.");
        }
    }

    @Nested
    @DisplayName("AcessDeniedException -> 403")
    class AcessDenied {

        @Test
        @DisplayName("deve retornar 403 com mensagem genérica (não revela detalhes)")
        void shouldReturn403WithAcessDenied() {

            var ex = new AccessDeniedException("Access is denied");

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleAccessDeniedException(ex, mockHttpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().message()).isEqualTo("Sem permissão para acessar este recurso");

            assertThat(response.getBody().message()).doesNotContain("Access is denied");
        }
    }

    @Nested
    @DisplayName("Exception genérica -> 500")
    class GenericError {

        @Test
        @DisplayName("deve retornar 500 com mensagem genérica (nunca stack trace)")
        void  shouldReturn500WithGenericError() {

            var ex = new RuntimeException("Detalhe interno do erro");

            ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(ex, mockHttpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().message()).doesNotContain("Detalhe interno do erro");
            assertThat(response.getBody().message()).contains("Erro interno");
        }
    }
}
