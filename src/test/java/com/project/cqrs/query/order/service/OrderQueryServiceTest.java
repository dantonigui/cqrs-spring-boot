package com.project.cqrs.query.order.service;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.payment.repository.PaymentQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class OrderQueryServiceTest {
    @Test void rejectsAnotherOwner() {
        var repo = mock(OrderQueryRepository.class); var payments = mock(PaymentQueryRepository.class);
        var order = OrderQueryEntity.fromCreatedEvent(1L, 2L, OrderStatus.PENDING, new BigDecimal("10.00"), LocalDateTime.now());
        when(repo.findByOrderId(1L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> new OrderQueryService(repo, payments).findByOrderId(1L, 3L)).isInstanceOf(com.project.cqrs.config.exception.ResourceNotFoundException.class); verifyNoInteractions(payments);
    }
    @Test void returnsOwnerDetails() {
        var repo = mock(OrderQueryRepository.class); var payments = mock(PaymentQueryRepository.class);
        var order = OrderQueryEntity.fromCreatedEvent(1L, 2L, OrderStatus.PENDING, new BigDecimal("10.00"), LocalDateTime.now());
        when(repo.findByOrderId(1L)).thenReturn(Optional.of(order));
        assertThat(new OrderQueryService(repo, payments).findByOrderId(1L, 2L).userId()).isEqualTo(2L);
    }
    @Test void returnsStablePageDto() {
        var repo = mock(OrderQueryRepository.class); var payments = mock(PaymentQueryRepository.class);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(repo.findByUserIdOrderByCreatedAtDesc(2L, pageable)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));
        assertThat(new OrderQueryService(repo, payments).findByUser(pageable, 2L).empty()).isTrue();
    }
}