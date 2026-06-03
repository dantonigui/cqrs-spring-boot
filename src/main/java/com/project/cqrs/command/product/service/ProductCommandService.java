package com.project.cqrs.command.product.service;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import com.project.cqrs.command.product.dto.request.CreateProductRequestDTO;
import com.project.cqrs.command.product.dto.request.UpdateProductRequestDTO;
import com.project.cqrs.config.redis.RedisConfig;
import com.project.cqrs.shared.event.product.ProductCreateEvent;
import com.project.cqrs.shared.event.product.ProductDeleteEvent;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
import com.project.cqrs.command.product.kafka.producer.ProductEventProducer;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import com.project.cqrs.command.product.repository.ProductRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductEventProducer eventProducer;

    public ProductCommandService(ProductRepository productRepository, CategoryRepository categoryRepository, ProductEventProducer eventProducer) {
        this.productRepository = productRepository;
        this.categoryRepository =  categoryRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    @CacheEvict(cacheNames = RedisConfig.CACHE_PRODUCTS, allEntries = true)
    public void createProduct(CreateProductRequestDTO productDto) {
        CategoryCommandEntity category = categoryRepository.findById(productDto.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found" + productDto.categoryId()));

        ProductCommandEntity product = ProductCommandEntity.createProduct(productDto.productName(), productDto.productCode(), productDto.productPrice(), productDto.productImage(), category);

        ProductCommandEntity savedProduct = productRepository.saveAndFlush(product);

        ProductCreateEvent event = ProductCreateEvent.fromEntity(savedProduct);
        eventProducer.sendProductCreated(savedProduct.getProductId().toString(), event);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.CACHE_PRODUCTS, allEntries = true),
            @CacheEvict(cacheNames = RedisConfig.CACHE_PRODUCT_DETAILS, key = "#id")
    })
    public void updateProduct(Long id, UpdateProductRequestDTO requestDTO) {
        ProductCommandEntity  productEntity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found" + id));

        CategoryCommandEntity categoryCommandEntity = categoryRepository.findById(requestDTO.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category Not Found" + requestDTO.categoryId()));

        requestDTO.applyTo(productEntity, categoryCommandEntity);

        ProductUpdateEvent event = ProductUpdateEvent.fromEntity(productEntity);
        eventProducer.sendProductUpdated(productEntity.getProductId().toString(), event);

        productRepository.save(productEntity);
    }


    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfig.CACHE_PRODUCTS, allEntries = true),
            @CacheEvict(cacheNames = RedisConfig.CACHE_PRODUCT_DETAILS, key = "#id")
    })
    public void deleteProduct(Long id) {
            ProductCommandEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found with id" + id));

            productRepository.delete(product);

            ProductDeleteEvent event = ProductDeleteEvent.fromEntity(product);
            eventProducer.sendProductDeleted(product.getProductId().toString(), event);
    }
}
