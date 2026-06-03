package com.project.cqrs.config.redis;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/admin/cache")
// @PreAuthorize("hasRole('ADMIN')")  // ← descomente com Spring Security
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
        long removed = cacheService.evictByPattern("cqrs:" + RedisConfig.CACHE_PRODUCTS + "::*");
        return ResponseEntity.ok(Map.of(
                "cache", RedisConfig.CACHE_PRODUCTS,
                "entriesRemoved", removed,
                "message", "Cache de lista de produtos invalidado"
        ));
    }

    /**
     * Invalida o detalhe de um produto específico.
     */
    @DeleteMapping("/product-detail/{id}")
    public ResponseEntity<Map<String, Object>> evictProductDetail(@PathVariable Long id) {
        String key = "cqrs:" + RedisConfig.CACHE_PRODUCT_DETAIL + "::" + id;
        boolean removed = cacheService.evict(key);
        return ResponseEntity.ok(Map.of(
                "cache", RedisConfig.CACHE_PRODUCT_DETAIL,
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
        String detailKey = "cqrs:" + RedisConfig.CACHE_PRODUCT_DETAIL + "::" + id;
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
        Set<String> productKeys = cacheService.listKeys("cqrs:" + RedisConfig.CACHE_PRODUCTS + "::*");
        Set<String> detailKeys  = cacheService.listKeys("cqrs:" + RedisConfig.CACHE_PRODUCT_DETAIL + "::*");

        return ResponseEntity.ok(Map.of(
                "productListKeys", productKeys,
                "productDetailKeys", detailKeys,
                "totalEntries", (productKeys != null ? productKeys.size() : 0)
                        + (detailKeys  != null ? detailKeys.size()  : 0)
        ));
    }
}