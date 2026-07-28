package com.project.cqrs.query.order.service;

import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService")
public class OrderQueryServiceTest {

    @Mock
    private OrderQueryRepository orderQueryRepository;

    private OrderQueryService orderQueryService;

    private static final Long ORDER_ID = 1L;
    private static final Long OWNER_ID = 42L;
    private static final Long OTHER_USER_ID = 999L;

    @BeforeEach
    void setUp() {
        orderQueryService = new OrderQueryService(orderQueryRepository);
    }

    private OrderQueryEntity mockOrder(Long userId) {
        OrderQueryEntity order = mock(OrderQueryEntity.class);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getUserId()).thenReturn(userId);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("100.00"));
        when(order.getItems()).thenReturn(List.of());
        when(order.getPayments()).thenReturn(List.of());
        return order;
    }

    // -- findByOrderId() -----------------------------------------------------

    @Nested
    @DisplayName("findByOrderId()")
    class FindByOrderId {

        @Test
        @DisplayName("deve lançar ResourceNotFoundException quando o pedido não existe")
        void shouldThrowWhenOrderDoesNotExist() {
            when(orderQueryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderQueryService.findByOrderId(ORDER_ID, OWNER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("""
            deve lançar ResourceNotFoundException (não AccessDenied)
            quando o pedido pertence a outro usuário
            """)
        void shouldThrowNotFoundWhenOrderBelongsToAnotherUser() {
            OrderQueryEntity order = mockOrder(OTHER_USER_ID);
            when(orderQueryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));

            // Deve ser especificamente ResourceNotFoundException — não deve
            // vazar um 403 que confirmaria a existência do pedido para
            // quem não é dono dele
            assertThatThrownBy(() -> orderQueryService.findByOrderId(ORDER_ID, OWNER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("deve retornar o DTO quando o pedido pertence ao usuário correto")
        void shouldReturnDtoWhenOwnershipMatches() {
            OrderQueryEntity order = mockOrder(OWNER_ID);
            when(orderQueryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(order));

            var result = orderQueryService.findByOrderId(ORDER_ID, OWNER_ID);

            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.userId()).isEqualTo(OWNER_ID);
        }
    }

    // -- FindByUser() --------------------------------------------------------

    @Nested
    @DisplayName("findByUser()")
    class FindByUser {

        @Test
        @DisplayName("deve retornar página de pedidos resumidos (sem itens/pagamentos")
        void shouldReturnSummaryPage() {
            OrderQueryEntity order = mockOrder(OWNER_ID);
            Pageable pageable = PageRequest.of(0, 10);
            Page<OrderQueryEntity> page = new PageImpl<>(List.of(order), pageable, 1);

            when(orderQueryRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID, pageable)).thenReturn(page);

            var result = orderQueryService.findByUser(pageable, OWNER_ID);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).items()).isEmpty();
            assertThat(result.getContent().get(0).payments()).isEmpty();
        }

        @Test
        @DisplayName("deve retornar página vazia quando o usuário não tem pedidos")
        void shouldReturnEmptyPageWhenUserIsEmpty() {
            Pageable pageable = PageRequest.of(0, 10);

            when(orderQueryRepository.findByUserIdOrderByCreatedAtDesc(OWNER_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(),  pageable, 0));

            var result = orderQueryService.findByUser(pageable, OWNER_ID);

            assertThat(result.getContent()).isEmpty();
        }
    }
}
