package com.project.cqrs.admin.cache.service;


import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.shared.redis.topics.CacheTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@org.springframework.transaction.annotation.Transactional("queryTransactionManager")
@Service
public class CacheSynchronizationService {

    private  final CacheManager cacheManager;
    private static final Logger log = LoggerFactory.getLogger(CacheSynchronizationService.class);
    public CacheSynchronizationService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void putProductDetailInCache(ProductQueryDTO dto) {
        Cache cache = cacheManager.getCache(CacheTopics.CACHE_PRODUCT_DETAILS);

        if(cache == null) {
            log.error("Cache {} não encontrado!", CacheTopics.CACHE_PRODUCT_DETAILS);
            return;
        }

        log.info("Salvando produto no cache id={}", dto.productId());

        cache.put(dto.productId(), dto);
    }

    public void putCategoryDetailInCache(CategoryQueryDTO dto) {
        Cache cache = cacheManager.getCache(CacheTopics.CACHE_CATEGORIES);

        if(cache != null) {
            cache.put(dto.categoryId(), dto);
        }
    }

    public void evictDetailFromCache(Long productId) {
        Cache cache = cacheManager.getCache(CacheTopics.CACHE_PRODUCT_DETAILS);
        if(cache != null) {
            cache.evict(productId);
        }
    }

    public void evictCategoryCache() {
        Cache cache = cacheManager.getCache(CacheTopics.CACHE_CATEGORIES);
        if (cache != null) cache.clear();
    }
    public void evictProductListCache() {
        Cache cache = cacheManager.getCache(CacheTopics.CACHE_PRODUCTS);
        if(cache != null) {
            cache.clear();
        }
    }
}
