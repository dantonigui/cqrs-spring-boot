package com.project.cqrs;
import com.project.cqrs.command.order.model.OrderCommandEntity;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.shared.enums.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class CqrsApplicationTests {
    @Test void entityDefaultsSurviveBuilder() {
        var order = OrderCommandEntity.create(1L, List.of());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING); assertThat(order.getCreatedAt()).isNotNull();
        var pix = PaymentCommandEntity.forPix(order);
        assertThat(pix.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING); assertThat(pix.getInstallments()).isEqualTo(1); assertThat(pix.getCreatedAt()).isNotNull();
    }
}