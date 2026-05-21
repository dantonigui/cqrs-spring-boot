package com.project.cqrs.query.auth.infra.kafka;

import com.project.cqrs.query.auth.dto.UserQueryDTO;
import com.project.cqrs.query.auth.model.UserQueryEntity;
import com.project.cqrs.query.auth.repository.UserQueryRepository;
import com.project.cqrs.shared.event.user.UserCreatedEvent;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import com.project.cqrs.shared.event.user.UserUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserEventConsumer {

    private final UserQueryRepository userQueryRepository;

    public UserEventConsumer(UserQueryRepository userQueryRepository) {
        this.userQueryRepository = userQueryRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.user-created}", groupId = "query-service")
    public void onUserCreated(UserCreatedEvent userCreatedEvent) {
        log.info("Received UserCreatedEvent {}", userCreatedEvent);

        if(userQueryRepository.existsById(userCreatedEvent.getUserId())) {
            log.warn("User {} already exists", userCreatedEvent.getUserId());
            return;
        }

        UserQueryEntity entity = UserQueryEntity.from(userCreatedEvent);
        userQueryRepository.save(entity);
    }

    @KafkaListener(topics = "${app.kafka.topics.user-updated}", groupId = "query-service")
    public void onUserUpdated(UserUpdatedEvent userUpdatedEvent) {
        log.info("Received UserUpdatedEvent {}", userUpdatedEvent);

        userQueryRepository.findById(userUpdatedEvent.getUserId())
                .ifPresentOrElse(user -> {
                    user.updateUserEmail(userUpdatedEvent.getEmail());
                    userQueryRepository.save(user);
                }, () -> log.warn("User not found with id {}", userUpdatedEvent.getUserId()));
    }

    @KafkaListener(topics = "${app.kafka.topics.user-logout}", groupId = "query-service")
    public void onUserLogout(UserLogoutEvent userLogoutEvent) {
        log.info("Received UserLogoutEvent {}", userLogoutEvent.getUserId());
    }
}
