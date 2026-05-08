package com.project.cqrs.command.product.service;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import com.project.cqrs.command.product.dto.request.CreateProductRequestDTO;
import com.project.cqrs.command.product.dto.request.UpdateProductRequestDTO;
import com.project.cqrs.command.product.event.ProductCreateEvent;
import com.project.cqrs.command.product.event.ProductDeleteEvent;
import com.project.cqrs.command.product.event.ProductUpdateEvent;
import com.project.cqrs.command.product.kafka.producer.ProductEventProducer;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import com.project.cqrs.command.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
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

    public void createProduct(CreateProductRequestDTO productDto) {
        CategoryCommandEntity category = categoryRepository.findById(productDto.categoryId())
                .orElseThrow(() -> new RuntimeException("Category Not Found"));

        ProductCommandEntity product = ProductCommandEntity.createProduct(productDto.productName(), productDto.productCode(), productDto.productPrice(), productDto.productImage(), category);

        productRepository.save(product);

        ProductCreateEvent event = ProductCreateEvent.fromEntity(product);
        eventProducer.sendProductCreated(product.getProductId().toString(), event);
    }

    public void updateProduct(Long id, UpdateProductRequestDTO requestDTO) {
        ProductCommandEntity  productEntity = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product Not Found with id" + id));

        CategoryCommandEntity categoryCommandEntity = categoryRepository.findById(requestDTO.categoryId())
                .orElseThrow(() -> new RuntimeException("Category Not Found"));

        requestDTO.applyTo(productEntity, categoryCommandEntity);

        ProductUpdateEvent event = ProductUpdateEvent.fromEntity(productEntity);
        eventProducer.sendProductUpdated(productEntity.getProductId().toString(), event);
    }



    public void deleteProduct(Long id) {
            ProductCommandEntity product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product Not Found with id" + id));

            productRepository.delete(product);

            ProductDeleteEvent event = ProductDeleteEvent.fromEntity(product);
            eventProducer.sendProductDeleted(product.getProductId().toString(), event);
    }
}
