package com.project.cqrs.admin.cache.service;

import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.config.redis.RedisConfig;
import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class CacheSynchronizationService {

    private  final CacheManager cacheManager;

    public CacheSynchronizationService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void putProductDetailInCache(ProductQueryDTO dto) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCT_DETAILS);

        if(cache != null) {
            cache.put(dto.productId(), dto);
        }
    }

    public void putCategoryDetailInCache(CategoryQueryDTO dto) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCT_DETAILS);

        if(cache != null) {
            cache.put(dto.categoryId(), dto);
        }
    }

    public void evictDetailFromCache(Long productId) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCT_DETAILS);
        if(cache != null) {
            cache.evict(productId);
        }
    }

    public void evictProductListCache() {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_PRODUCTS);
        if(cache != null) {
            cache.clear();
        }
    }
}
