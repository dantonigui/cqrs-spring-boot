package com.project.cqrs.command.auth.infra.kafka;

import com.project.cqrs.shared.event.user.UserCreatedEvent;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import com.project.cqrs.shared.event.user.UserUpdatedEvent;
import com.project.cqrs.shared.kafka.topics.UserTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.project.cqrs.command.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserEventProducer {

    private final OutboxPublisher  kafkaTemplate;

    public UserEventProducer(OutboxPublisher kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserCreatedEvent(UserCreatedEvent userCreatedEvent) {
        kafkaTemplate.publish(UserTopics.USER_CREATED, userCreatedEvent.getUserId().toString(), userCreatedEvent);
    }

    public void publishUserUpdatedEvent(UserUpdatedEvent userUpdatedEvent) {
        kafkaTemplate.publish(UserTopics.USER_UPDATED, userUpdatedEvent.getUserId().toString(),userUpdatedEvent);
    }

    public void publishUserLogoutEvent(UserLogoutEvent userLogoutEvent) {
        kafkaTemplate.publish(UserTopics.USER_LOGOUT, userLogoutEvent.getUserId().toString(),userLogoutEvent);
    }
}
