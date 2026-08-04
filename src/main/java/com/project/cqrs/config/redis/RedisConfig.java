package com.project.cqrs.config.redis;

import com.project.cqrs.shared.redis.topics.CacheTopics;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory, GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(genericJackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(genericJackson2JsonRedisSerializer);
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }


    private RedisCacheConfiguration defaultCacheConfig(GenericJackson2JsonRedisSerializer jsonSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer) {
        RedisCacheConfiguration redisCacheConfiguration = defaultCacheConfig(genericJackson2JsonRedisSerializer);

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put(CacheTopics.CACHE_PRODUCTS, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5)));

        cacheConfigs.put(CacheTopics.CACHE_PRODUCT_DETAILS, redisCacheConfiguration.entryTtl(Duration.ofMinutes(10)));

        cacheConfigs.put(CacheTopics.CACHE_CATEGORIES,
                redisCacheConfiguration.entryTtl(Duration.ofMinutes(30)));

        cacheConfigs.put(CacheTopics.CACHE_ORDERS, redisCacheConfiguration.entryTtl(Duration.ofMinutes(2)));

        cacheConfigs.put(CacheTopics.CACHE_ORDER_DETAIL, redisCacheConfiguration.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }

}
