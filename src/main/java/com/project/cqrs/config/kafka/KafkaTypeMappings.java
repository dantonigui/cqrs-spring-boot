package com.project.cqrs.config.kafka;

import com.project.cqrs.shared.event.category.CategoryCreateEvent;
import com.project.cqrs.shared.event.category.CategoryDeleteEvent;
import com.project.cqrs.shared.event.category.CategoryUpdateEvent;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import com.project.cqrs.shared.event.product.ProductCreateEvent;
import com.project.cqrs.shared.event.product.ProductDeleteEvent;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
import com.project.cqrs.shared.event.user.UserCreatedEvent;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import com.project.cqrs.shared.event.user.UserUpdatedEvent;
import com.project.cqrs.shared.kafka.KafkaEventAliases;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class KafkaTypeMappings {

    private final Map<String, Class<?>> mappings = new LinkedHashMap<>();

    public KafkaTypeMappings() {

        // User
        mappings.put(KafkaEventAliases.USER_CREATED, UserCreatedEvent.class);
        mappings.put(KafkaEventAliases.USER_UPDATED, UserUpdatedEvent.class);
        mappings.put(KafkaEventAliases.USER_LOGOUT, UserLogoutEvent.class);

        // Category
        mappings.put(KafkaEventAliases.CATEGORY_CREATED, CategoryCreateEvent.class);
        mappings.put(KafkaEventAliases.CATEGORY_UPDATED, CategoryUpdateEvent.class);
        mappings.put(KafkaEventAliases.CATEGORY_DELETED, CategoryDeleteEvent.class);

        // Product
        mappings.put(KafkaEventAliases.PRODUCT_CREATED, ProductCreateEvent.class);
        mappings.put(KafkaEventAliases.PRODUCT_UPDATED, ProductUpdateEvent.class);
        mappings.put(KafkaEventAliases.PRODUCT_DELETED, ProductDeleteEvent.class);

        // Order
        mappings.put(KafkaEventAliases.ORDER_CREATED, OrderCreatedEvent.class);
        mappings.put(KafkaEventAliases.ORDER_UPDATED, OrderStatusChangedEvent.class);
        mappings.put(KafkaEventAliases.ORDER_DELETED, OrderCancelledEvent.class);

        // Payment
        mappings.put(KafkaEventAliases.PAYMENT_APPROVED, PaymentApprovedEvent.class);
    }

    public String getTypeMappingsProperty() {

        StringBuilder builder = new StringBuilder();

        mappings.forEach((alias, clazz) -> {

            if (!builder.isEmpty()) {
                builder.append(",");
            }

            builder.append(alias)
                    .append(":")
                    .append(clazz.getName());

        });

        return builder.toString();
    }
}