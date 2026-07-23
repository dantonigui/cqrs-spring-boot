package com.project.cqrs.query.product.service;

import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.config.redis.RedisConfig;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import com.project.cqrs.shared.dto.PageResponseDTO;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryService.class);
    private final ProductQueryRepository productRepository;

    public ProductQueryService(ProductQueryRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(cacheNames = RedisConfig.CACHE_PRODUCTS,
            key = "'page-' + #pageable.pageNumber + '-size-' + #pageable.pageSize",
            unless = "#result == null || #result.content().isEmpty()")
    @Transactional(readOnly = true)
    public Page<ProductQueryDTO> findAll(Pageable pageable) {

        log.debug("Cache MISS — buscando produtos no MySQL. Page: {}", pageable.getPageNumber());

        return productRepository.findByOrderByProductNameAsc(pageable)
                .map(ProductQueryDTO::from);
    }

    @Cacheable(
            cacheNames = RedisConfig.CACHE_PRODUCT_DETAILS,
            key = "#id",
            unless = "#result == null"
    )
    @Transactional(readOnly = true)
    public ProductQueryDTO findById(Long id) {
        return productRepository.findById(id)
                .map(ProductQueryDTO::from)
                .orElseThrow(()-> new ResourceNotFoundException("Product not found" + id));
    }

    @Cacheable(
            cacheNames = RedisConfig.CACHE_PRODUCTS,
            key = "'category-' + #categoryId + '-page-' + #pageable.pageNumber "
                    + "+ '-size-' + #pageable.pageSize",
            unless = "#result == null || #result.isEmpty()"
    )
    public Page<ProductQueryDTO> findByCategoryId(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId,pageable).map(ProductQueryDTO::from);
    }
}
