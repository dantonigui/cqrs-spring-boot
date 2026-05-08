package com.project.cqrs.query.product.kafka.consumer;

import com.project.cqrs.command.product.event.ProductCreateEvent;
import com.project.cqrs.command.product.event.ProductDeleteEvent;
import com.project.cqrs.command.product.event.ProductUpdateEvent;
import com.project.cqrs.query.product.model.ProductQueryEntity;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ProductEventConsumer {

    private final ProductQueryRepository repository;

    public ProductEventConsumer(ProductQueryRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "products-created", groupId = "product-group")
    public void OnProductCreated(ProductCreateEvent event) {
        repository.findById(event.getProductId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setProductName(event.getProductName());
                            existing.setProductCode(event.getProductCode());
                            existing.setProductPrice(event.getProductPrice());
                            existing.setProductImage(event.getProductImage());
                            existing.setCategoryId(event.getCategoryId());
                            repository.save(existing);
                        },
                        () -> repository.save(new ProductQueryEntity(
                                event.getProductId(),
                                event.getProductName(),
                                event.getProductCode(),
                                event.getProductPrice(),
                                event.getProductImage(),
                                event.getCategoryId()
                        ))
                );

        System.out.println("EVENTO RECEBIDO: " + event);
    }

    @KafkaListener(topics = "products-updated", groupId = "product-group")
    public void OnProductUpdated(ProductUpdateEvent event) {
        repository.findById(event.getProductId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setProductName(event.getProductName());
                            existing.setProductCode(event.getProductCode());
                            existing.setProductPrice(event.getProductPrice());
                            existing.setProductImage(event.getProductImage());
                            existing.setCategoryId(event.getCategoryId());
                            repository.save(existing);
                        },
                        () -> { throw new RuntimeException("Product not found: " + event.getProductId()); }
                );
    }

    @KafkaListener(topics = "products-deleted", groupId = "product-group")
    public void OnProductDeleted(ProductDeleteEvent event) {
        repository.deleteById(event.getProductId());
    }
}