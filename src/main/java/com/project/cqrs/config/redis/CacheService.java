package com.project.cqrs.config.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;


@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    public long evictByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(keys);
        long count = deleted != null ? deleted : 0;
        log.info("Evict por padrão '{}': {} chaves removidas", pattern, count);
        return count;
    }

    public void put(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Cache put: key={}, ttl={}s", key, ttlSeconds);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean evict(String key) {
        Boolean deleted = redisTemplate.delete(key);
        return Boolean.TRUE.equals(deleted);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public long getTtl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2;
    }

    public Set<String> listKeys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    public void evictAllProductCaches() {
        long listCount   = evictByPattern("cqrs:" + RedisConfig.CACHE_PRODUCTS + "::*");
        long detailCount = evictByPattern("cqrs:" + RedisConfig.CACHE_PRODUCT_DETAILS + "::*");
        log.warn("Full cache evict: {} entradas de lista + {} detalhes removidos", listCount, detailCount);
    }
}