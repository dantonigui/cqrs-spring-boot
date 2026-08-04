package com.project.cqrs.admin.cache.controller;

import com.project.cqrs.admin.cache.service.CacheService;
import com.project.cqrs.config.redis.RedisConfig;
import com.project.cqrs.shared.redis.topics.CacheTopics;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/admin/cache")
@PreAuthorize("hasRole('ADMIN')")
public class CacheAdminController {

    private final CacheService cacheService;

    public CacheAdminController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Invalida toda a lista paginada de produtos.
     */
    @DeleteMapping("/products")
    public ResponseEntity<Map<String, Object>> evictProductList() {
        long removed = cacheService.evictByPattern("cqrs:" + CacheTopics.CACHE_PRODUCTS + "::*");
        return ResponseEntity.ok(Map.of(
                "cache", CacheTopics.CACHE_PRODUCTS,
                "entriesRemoved", removed,
                "message", "Cache de lista de produtos invalidado"
        ));
    }

    /**
     * Invalida o detalhe de um produto específico.
     */
    @DeleteMapping("/product-detail/{id}")
    public ResponseEntity<Map<String, Object>> evictProductDetail(@PathVariable Long id) {
        String key = "cqrs:" + CacheTopics.CACHE_PRODUCT_DETAILS + "::" + id;
        boolean removed = cacheService.evict(key);
        return ResponseEntity.ok(Map.of(
                "cache", CacheTopics.CACHE_PRODUCT_DETAILS,
                "productId", id,
                "removed", removed
        ));
    }

    /**
     * Limpa todos os caches de produtos (lista + detalhes).
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> evictAll() {
        cacheService.evictAllProductCaches();
        return ResponseEntity.ok(Map.of("message", "Todos os caches de produto foram invalidados"));
    }

    /**
     * Retorna estatísticas básicas de uma entrada no cache.
     */
    @GetMapping("/stats/{id}")
    public ResponseEntity<Map<String, Object>> cacheStats(@PathVariable Long id) {
        String detailKey = "cqrs:" + CacheTopics.CACHE_PRODUCT_DETAILS + "::" + id;
        boolean exists = cacheService.exists(detailKey);
        long ttl = cacheService.getTtl(detailKey);

        return ResponseEntity.ok(Map.of(
                "productId", id,
                "cacheKey", detailKey,
                "exists", exists,
                "ttlSeconds", ttl,
                "status", exists ? "HIT" : "MISS"
        ));
    }

    /**
     * Lista todas as chaves do cache de produtos (diagnóstico — não usar em produção com volume alto).
     */
    @GetMapping("/keys")
    public ResponseEntity<Map<String, Object>> listCacheKeys() {
        Set<String> productKeys = cacheService.listKeys("cqrs:" + CacheTopics.CACHE_PRODUCTS + "::*");
        Set<String> detailKeys  = cacheService.listKeys("cqrs:" + CacheTopics.CACHE_PRODUCT_DETAILS + "::*");

        return ResponseEntity.ok(Map.of(
                "productListKeys", productKeys,
                "productDetailKeys", detailKeys,
                "totalEntries", (productKeys != null ? productKeys.size() : 0)
                        + (detailKeys  != null ? detailKeys.size()  : 0)
        ));
    }
}