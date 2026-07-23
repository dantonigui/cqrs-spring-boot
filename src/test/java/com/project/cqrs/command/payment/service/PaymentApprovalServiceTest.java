package com.project.cqrs.command.payment.service;

import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.kafka.producer.PaymentEventProducer;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.enums.PaymentMethod;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApprovalService")
public class PaymentApprovalServiceTest {

    @Mock
    private OrderCommandRepository orderCommandRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    private PaymentApprovalService paymentApprovalService;

    private static final String MP_PAYMENT_ID = "mp-pay-123";
    private static final Long   ORDER_ID      = 1L;
    private static final Long   USER_ID       = 42L;

    @BeforeEach
    void setUp() {
        paymentApprovalService = new PaymentApprovalService(orderCommandRepository, paymentEventProducer);
    }

    // -- Helpers -------------------------------------------------------------

    private OrderCommandEntity mockOrder(OrderStatus orderStatus) {
        OrderCommandEntity orderEntity = mock(OrderCommandEntity.class);
        when(orderEntity.getId()).thenReturn(ORDER_ID);
        when(orderEntity.getUserId()).thenReturn(USER_ID);
        when(orderEntity.getStatus()).thenReturn(orderStatus);

        return orderEntity;
    }

    private PaymentCommandEntity mockPayment(OrderCommandEntity orderEntity) {
        PaymentCommandEntity paymentEntity = mock(PaymentCommandEntity.class);
        when(paymentEntity.getId()).thenReturn(10L);
        when(paymentEntity.getOrder()).thenReturn(orderEntity);
        when(paymentEntity.getTransactionAmount()).thenReturn(new BigDecimal("199.90"));
        when(paymentEntity.getPaymentMethod()).thenReturn(PaymentMethod.PIX);
        return paymentEntity;
    }

    // -- Aprovação normal ----------------------------------------------------

    @Nested
    @DisplayName("approve() - fluxo normal")
    class ApproveNormal {

        @Test
        @DisplayName("deve marcar o pedido como PAID")
        void shouldMarkOrderAsPaid() {

            OrderCommandEntity orderEntity = mockOrder(OrderStatus.AWAITING_PAYMENT);
            PaymentCommandEntity paymentEntity = mockPayment(orderEntity);

            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(orderEntity));

            paymentApprovalService.approve(paymentEntity, MP_PAYMENT_ID);

            verify(orderEntity).markAsPaid();

            verify(orderCommandRepository).save(orderEntity);
        }

        @Test
        @DisplayName("deve publicar PaymentApprovedEvent no Kafka")
        void shouldPublishPaymentApprovedEvent() {
            OrderCommandEntity orderEntity = mockOrder(OrderStatus.AWAITING_PAYMENT);
            PaymentCommandEntity paymentEntity = mockPayment(orderEntity);

            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(orderEntity));

            paymentApprovalService.approve(paymentEntity, MP_PAYMENT_ID);

            ArgumentCaptor<PaymentApprovedEvent> captor = ArgumentCaptor.forClass(PaymentApprovedEvent.class);

            verify(paymentEventProducer).publishPaymentApproved(eq(ORDER_ID.toString()), captor.capture());

            PaymentApprovedEvent event = captor.getValue();
            assertThat(event.orderId()).isEqualTo(ORDER_ID);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("199.90"));
            assertThat(event.paymentMethod()).isEqualTo(PaymentMethod.PIX);
        }

        @Test
        @DisplayName("eventId deve ser determinístico baseado no mpPaymentId")
        void eventIdShouldBeDeterministic() {
            OrderCommandEntity orderEntity = mockOrder(OrderStatus.AWAITING_PAYMENT);
            PaymentCommandEntity paymentEntity = mockPayment(orderEntity);
            when(orderCommandRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(orderEntity));

            paymentApprovalService.approve(paymentEntity, MP_PAYMENT_ID);
            paymentApprovalService.approve(paymentEntity, MP_PAYMENT_ID);

            ArgumentCaptor<PaymentApprovedEvent> captor = ArgumentCaptor.forClass(PaymentApprovedEvent.class);

            verify(paymentEventProducer, times(2)).publishPaymentApproved(eq(ORDER_ID.toString()), captor.capture());

            List<PaymentApprovedEvent> events = captor.getAllValues();

            assertThat(events.get(0).eventId())
                    .isEqualTo(events.get(1).eventId());
        }
    }

    // -- Idempotência --------------------------------------------------------

    @Nested
    @DisplayName("approve() - idempotência")
    class  ApproveIdempotency {

        @Test
        @DisplayName("deve ignorar se o pedido já está PAID")
        void shouldSkipIfOrderAlreadyPaid() {

            OrderCommandEntity orderEntity = mock(OrderCommandEntity.class);
            PaymentCommandEntity paymentEntity = mock(PaymentCommandEntity.class);

            when(paymentEntity.getOrder())
                    .thenReturn(orderEntity);

            when(orderEntity.getId())
                    .thenReturn(ORDER_ID);

            when(orderEntity.getStatus())
                    .thenReturn(OrderStatus.PAID);

            when(orderCommandRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.of(orderEntity));

            paymentApprovalService.approve(paymentEntity, MP_PAYMENT_ID);

            verify(orderEntity, never()).markAsPaid();
            verify(orderCommandRepository, never()).save(any());
            verify(paymentEventProducer, never())
                    .publishPaymentApproved(any(), any());
        }

        @Test
        @DisplayName("deve ignorar quando pedido não existe")
        void shouldIgnoreIfOrderNotFound() {

            OrderCommandEntity orderEntity = mock(OrderCommandEntity.class);
            PaymentCommandEntity paymentEntity = mock(PaymentCommandEntity.class);

            when(paymentEntity.getOrder())
                    .thenReturn(orderEntity);

            when(orderEntity.getId())
                    .thenReturn(ORDER_ID);

            when(orderCommandRepository.findByIdForUpdate(ORDER_ID))
                    .thenReturn(Optional.empty());

            paymentApprovalService.approve(paymentEntity, MP_PAYMENT_ID);

            verify(orderCommandRepository)
                    .findByIdForUpdate(ORDER_ID);

            verify(orderCommandRepository, never())
                    .save(any());

            verify(paymentEventProducer, never())
                    .publishPaymentApproved(any(), any());
        }
    }

}
