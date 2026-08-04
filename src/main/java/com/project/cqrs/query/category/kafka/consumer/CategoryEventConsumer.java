package com.project.cqrs.query.category.kafka.consumer;

import com.project.cqrs.admin.cache.service.CacheSynchronizationService;
import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.shared.event.category.CategoryCreateEvent;
import com.project.cqrs.shared.event.category.CategoryDeleteEvent;
import com.project.cqrs.shared.event.category.CategoryUpdateEvent;
import com.project.cqrs.query.category.model.CategoryQueryEntity;
import com.project.cqrs.query.category.repository.CategoryQueryRepository;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import com.project.cqrs.shared.kafka.groupId.KafkaConsumerGroups;
import com.project.cqrs.shared.kafka.topics.CategoryTopics;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Transactional
public class CategoryEventConsumer {

    private static final Logger log =  LoggerFactory.getLogger(CategoryEventConsumer.class);
    private final CategoryQueryRepository categoryQueryRepository;
    private final IdempotencyService  idempotencyService;
    private final CacheSynchronizationService  cacheSynchronizationService;

    public CategoryEventConsumer(CategoryQueryRepository categoryQueryRepository, IdempotencyService idempotencyService,  CacheSynchronizationService cacheSynchronizationService) {
        this.categoryQueryRepository = categoryQueryRepository;
        this.idempotencyService = idempotencyService;
        this.cacheSynchronizationService = cacheSynchronizationService;
    }

    @KafkaListener(topics = CategoryTopics.CATEGORY_CREATED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onCategoriesCreated(CategoryCreateEvent event, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        log.info(
                "Recebido CategoryCreateEvent: id={}, nome={}",
                event.getCategoryId(),
                event.getCategoryName()
        );

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), CategoryTopics.CATEGORY_CREATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            CategoryQueryEntity entity = CategoryQueryEntity.fromcreateEvent(event.getCategoryId(),event.getCategoryName());

            CategoryQueryEntity saved = categoryQueryRepository.save(entity);

            cacheSynchronizationService.putCategoryDetailInCache(CategoryQueryDTO.from(saved));

            cacheSynchronizationService.evictProductListCache();

            idempotencyService.markCompleted(processed);

            log.info(
                    "Processando category.created: categoryId={}",
                    event.getCategoryId()
            );

        } catch (Exception e) {

            idempotencyService.markFailed(processed, e);
            throw e;
        }
    }

    @KafkaListener(topics = CategoryTopics.CATEGORY_UPDATED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onCategoriesUpdated(CategoryUpdateEvent event, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), CategoryTopics.CATEGORY_UPDATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            CategoryQueryEntity entity = categoryQueryRepository.findById(event.getCategoryId())
                    .orElseThrow(() -> new IllegalStateException("Category not found" +  event.getCategoryId()));

            entity.appyUpdateEvent(event);

            CategoryQueryEntity saved = categoryQueryRepository.save(entity);

            cacheSynchronizationService.putCategoryDetailInCache(CategoryQueryDTO.from(saved));

            cacheSynchronizationService.evictProductListCache();

            idempotencyService.markCompleted(processed);

        } catch (Exception e) {

            idempotencyService.markFailed(processed, e);

            throw e;
        }
    }

    @KafkaListener(topics = CategoryTopics.CATEGORY_DELETED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onCategoriesDeleted(CategoryDeleteEvent event, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), CategoryTopics.CATEGORY_DELETED, deliveryAttempt);

        if (processed ==  null) {
            return;
        }

        try {
            categoryQueryRepository.findById(event.getCategoryId())
                    .ifPresent(categoryQueryRepository::delete);

            cacheSynchronizationService.evictDetailFromCache(event.getCategoryId());

            cacheSynchronizationService.evictProductListCache();

            idempotencyService.markCompleted(processed);
        } catch (Exception e) {
            idempotencyService.markFailed(processed, e);

            throw e;
        }
    }
}
