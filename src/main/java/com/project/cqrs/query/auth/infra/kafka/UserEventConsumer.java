package com.project.cqrs.query.auth.infra.kafka;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.auth.model.UserQueryEntity;
import com.project.cqrs.query.auth.repository.UserQueryRepository;
import com.project.cqrs.shared.event.user.UserCreatedEvent;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import com.project.cqrs.shared.event.user.UserUpdatedEvent;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import com.project.cqrs.shared.kafka.groupId.KafkaConsumerGroups;
import com.project.cqrs.shared.kafka.topics.UserTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserEventConsumer {

    private final UserQueryRepository userQueryRepository;
    private final IdempotencyService  idempotencyService;

    public UserEventConsumer(UserQueryRepository userQueryRepository, IdempotencyService idempotencyService) {
        this.userQueryRepository = userQueryRepository;
        this.idempotencyService = idempotencyService;
    }

    @KafkaListener(topics = UserTopics.USER_CREATED, groupId = KafkaConsumerGroups.QUERY,  containerFactory = KafkaContainerFactories.RESILIENT)
    public void onUserCreated(UserCreatedEvent userCreatedEvent, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        log.info(
                "Processando user.created: userId={}",
                userCreatedEvent.getUserId()
        );

        ProcessedEventEntity processed = idempotencyService.tryClaim(userCreatedEvent.getEventId(), UserTopics.USER_CREATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {

            UserQueryEntity entity = UserQueryEntity.from(userCreatedEvent);

            userQueryRepository.save(entity);

            idempotencyService.markCompleted(processed);

            log.info(
                    "User created: userId={}",
                    userCreatedEvent.getUserId()
            );
        } catch (Exception e) {

            idempotencyService.markFailed(processed);
            throw e;
        }

    }

    @KafkaListener(topics = UserTopics.USER_UPDATED, groupId = KafkaConsumerGroups.QUERY,  containerFactory = KafkaContainerFactories.RESILIENT)
    public void onUserUpdated(UserUpdatedEvent userUpdatedEvent,@Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        log.info(
                "Processando user.updated: userId={}",
                userUpdatedEvent.getUserId()
        );

        ProcessedEventEntity processed = idempotencyService.tryClaim(userUpdatedEvent.getEventId(), UserTopics.USER_UPDATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            UserQueryEntity user = userQueryRepository.findById(userUpdatedEvent.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found" +  userUpdatedEvent.getUserId()));

            user.applyUpdateEvent(userUpdatedEvent);

            userQueryRepository.save(user);

            idempotencyService.markCompleted(processed);

            log.info(
                    "User updated: userId={}",
                    userUpdatedEvent.getUserId()
            );
        } catch (Exception e) {
            idempotencyService.markFailed(processed);
            throw e;
        }


    }

    @KafkaListener(topics = UserTopics.USER_LOGOUT, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onUserLogout(UserLogoutEvent userLogoutEvent, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(userLogoutEvent.getEventId(), UserTopics.USER_LOGOUT, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            idempotencyService.markCompleted(processed);
        }  catch (Exception e) {
            idempotencyService.markFailed(processed);
            throw e;
        }
    }
}
