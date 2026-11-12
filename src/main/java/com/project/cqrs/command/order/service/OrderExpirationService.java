package com.project.cqrs.command.order.service;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import org.slf4j.*;
@Service
public class OrderExpirationService {
    private static final Logger log = LoggerFactory.getLogger(OrderExpirationService.class);
    private final OrderCommandRepository orders;
    private final OrderCancelledService cancellation;
    private final long minutes;
    public OrderExpirationService(OrderCommandRepository orders, OrderCancelledService cancellation, @Value("${app.orders.reservation-minutes:30}") long minutes) {
        this.orders = orders; this.cancellation = cancellation; this.minutes = minutes;
    }
    @Scheduled(fixedDelayString = "${app.orders.expiration-poll-ms:60000}")
    public void expire() {
        for (var order : orders.expired(LocalDateTime.now().minusMinutes(minutes), PageRequest.of(0, 100))) {
            try { cancellation.expireOrder(order.getOrderId(), order.getUserId()); }
            catch (Exception e) { log.warn("Pedido {} não expirou; nova tentativa no próximo ciclo", order.getOrderId(), e); }
        }
    }
}