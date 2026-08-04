package com.project.cqrs.query.product.kafka.consumer;

import com.project.cqrs.admin.cache.service.CacheSynchronizationService;
import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.config.redis.RedisConfig;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import com.project.cqrs.shared.kafka.groupId.KafkaConsumerGroups;
import com.project.cqrs.shared.kafka.topics.ProductTopics;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.shared.event.product.ProductCreateEvent;
import com.project.cqrs.shared.event.product.ProductDeleteEvent;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
import com.project.cqrs.query.product.model.ProductQueryEntity;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);
    private final ProductQueryRepository repository;
    private final IdempotencyService idempotencyService;
    private final CacheSynchronizationService  cacheSynchronizationService;

    public ProductEventConsumer(ProductQueryRepository repository,
                                IdempotencyService idempotencyService,  CacheSynchronizationService cacheSynchronizationService) {
        this.repository = repository;
        this.idempotencyService = idempotencyService;
        this.cacheSynchronizationService = cacheSynchronizationService;
    }

    @KafkaListener(topics = ProductTopics.PRODUCTS_CREATED, groupId= KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void OnProductCreated(ProductCreateEvent event, @Header(KafkaHeaders.DELIVERY_ATTEMPT) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), ProductTopics.PRODUCTS_CREATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            ProductQueryEntity entity = ProductQueryEntity.fromCreateEvent(event);

            ProductQueryEntity saved =  repository.save(entity);

            cacheSynchronizationService.putProductDetailInCache(ProductQueryDTO.from(saved));

            cacheSynchronizationService.evictProductListCache();

            idempotencyService.markCompleted(processed);

            log.info("Cache sincronizado após criação do produto id={}", event.getProductId());
        } catch (Exception e) {
            idempotencyService.markFailed(processed);
            throw e;
        }
    }

    @KafkaListener(topics = ProductTopics.PRODUCTS_CREATED,  groupId= KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void OnProductUpdated(ProductUpdateEvent event, @Header(KafkaHeaders.DELIVERY_ATTEMPT) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), ProductTopics.PRODUCTS_CREATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            ProductQueryEntity entity = repository.findById(event.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Produto não encontrado: " + event.getProductId()));

            entity.applyUpdatedEvent(event);

            ProductQueryEntity saved =  repository.save(entity);

            cacheSynchronizationService.putProductDetailInCache(ProductQueryDTO.from(saved));

            cacheSynchronizationService.evictProductListCache();

            idempotencyService.markCompleted(processed);

        }  catch (Exception e) {
            idempotencyService.markFailed(processed);
            throw e;
        }


    }

    @KafkaListener(topics = ProductTopics.PRODUCTS_DELETED,  groupId= KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void OnProductDeleted(ProductDeleteEvent event, @Header(KafkaHeaders.DELIVERY_ATTEMPT) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), ProductTopics.PRODUCTS_DELETED,deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            log.info("Processando product-deleted: eventId={}, productId={}", event.getEventId(), event.getProductId());

            repository.findById(event.getProductId())
                    .ifPresent(repository::delete);

            cacheSynchronizationService.evictDetailFromCache(event.getProductId());

            cacheSynchronizationService.evictProductListCache();

            idempotencyService.markCompleted(processed);

        } catch (Exception e) {
            idempotencyService.markFailed(processed);

            throw e;
        }
    }
}