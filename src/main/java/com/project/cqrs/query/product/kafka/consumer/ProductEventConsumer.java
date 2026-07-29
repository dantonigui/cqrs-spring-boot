package com.project.cqrs.query.product.kafka.consumer;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.config.redis.RedisConfig;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);
    private final ProductQueryRepository repository;
    private final CacheManager cacheManager;
    private final IdempotencyService idempotencyService;

    public ProductEventConsumer(ProductQueryRepository repository, CacheManager cacheManager, IdempotencyService idempotencyService) {
        this.repository = repository;
        this.cacheManager = cacheManager;
        this.idempotencyService = idempotencyService;
    }

    @KafkaListener(topics = "products-created", groupId = "product-group")
    public void OnProductCreated(ProductCreateEvent event) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), "product-created");

        if (processed == null) {
            return;
        }

        try {
            ProductQueryEntity entity = ProductQueryEntity.fromCreateEvent(event);

            ProductQueryEntity saved =  repository.save(entity);

            putDetailInCache(ProductQueryDTO.from(saved));

            evictProductListCache();

            log.info("Cache sincronizado após criação do produto id={}", event.getProductId());
        } catch (Exception e) {
            idempotencyService.markFailed(processed);
            throw e;
        }
    }

    @KafkaListener(topics = "products-updated", groupId = "product-group")
    public void OnProductUpdated(ProductUpdateEvent event) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), "product-updated");

        if (processed == null) {
            return;
        }

        try {
            ProductQueryEntity entity = repository.findById(event.getProductId())
                    .orElseThrow(() -> new IllegalStateException("Produto não encontrado: " + event.getProductId()));

            entity.applyUpdatedEvent(event);

            ProductQueryEntity saved =  repository.save(entity);

            putDetailInCache(ProductQueryDTO.from(saved));

            evictProductListCache();

        }  catch (Exception e) {
            idempotencyService.markFailed(processed);
            throw e;
        }


    }

    @KafkaListener(topics = "products-deleted", groupId = "product-group")
    public void OnProductDeleted(ProductDeleteEvent event) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(event.getEventId(), "product-deleted");

        if (processed == null) {
            return;
        }

        try {
            log.info("Processando product-deleted: eventId={}, productId={}", event.getEventId(), event.getProductId());

            repository.findById(event.getProductId())
                    .ifPresent(repository::delete);

            evictDetailFromCache(event.getProductId());

            evictProductListCache();

        } catch (Exception e) {
            idempotencyService.markFailed(processed);

            throw e;
        }
    }

    private void putDetailInCache(ProductQueryDTO dto) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCT_DETAILS);

        if(cache != null) {
            cache.put(dto.productId(), dto);
        }
    }

    private void evictDetailFromCache(Long productId) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCT_DETAILS);
        if(cache != null) {
            cache.evict(productId);
        }
    }

    private void evictProductListCache() {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCTS);
        if(cache != null) {
            cache.clear();
        }
    }
}