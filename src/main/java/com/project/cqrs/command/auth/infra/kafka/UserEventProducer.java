package com.project.cqrs.command.auth.infra.kafka;

import com.project.cqrs.shared.event.user.UserCreatedEvent;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import com.project.cqrs.shared.event.user.UserUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserEventProducer {

    private final KafkaTemplate<String, Object>  kafkaTemplate;

    @Value("${app.kafka.topics.user-created}") private String userCreatedTopic;
    @Value("${app.kafka.topics.user-updated}") private String userUpdatedTopic;
    @Value("${app.kafka.topics.user-logout}")  private String userLogoutTopic;

    public UserEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserCreatedEvent(UserCreatedEvent userCreatedEvent) {
        kafkaTemplate.send(userCreatedTopic, userCreatedEvent.getUserId().toString(), userCreatedEvent).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Falha ao publicar UserCreatedEvent", userCreatedEvent.getUserId(), error);
            } else {
                log.info("UserCreatedEvent publicado userId={}", userCreatedEvent.getUserId());
            }
        });
    }

    public void publishUserUpdatedEvent(UserUpdatedEvent userUpdatedEvent) {
        kafkaTemplate.send(userUpdatedTopic, userUpdatedEvent.getUserId().toString(),userUpdatedEvent).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Falha ao publicar UserUpdatedEvent", userUpdatedEvent.getUserId(), error);
            } else  {
                log.info("UserUpdatedEvent publicado userId={}", userUpdatedEvent.getUserId());
            }
        });
    }

    public void publishUserLogoutEvent(UserLogoutEvent userLogoutEvent) {
        kafkaTemplate.send(userLogoutTopic, userLogoutEvent.getUserId().toString(),userLogoutEvent).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Falha ao publicar UserLogoutEvent", userLogoutEvent.getUserId());
            } else {
                log.info("UserLogoutEvent publicado userId={}", userLogoutEvent.getUserId());
            }
        });
    }
}
