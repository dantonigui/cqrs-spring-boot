package com.project.cqrs.query.category.kafka.consumer;

import com.project.cqrs.command.category.event.CategoryCreateEvent;
import com.project.cqrs.command.category.event.CategoryDeleteEvent;
import com.project.cqrs.command.category.event.CategoryUpdateEvent;
import com.project.cqrs.query.category.model.CategoryQueryEntity;
import com.project.cqrs.query.category.repository.CategoryQueryRepository;
import jakarta.transaction.Transactional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Transactional
public class CategoryEventConsumer {

    private final CategoryQueryRepository categoryQueryRepository;

    public CategoryEventConsumer(CategoryQueryRepository categoryQueryRepository) {
        this.categoryQueryRepository = categoryQueryRepository;
    }

    @KafkaListener(topics = "category-created", groupId = "category-group")
    public void OnCategoriesCreated(CategoryCreateEvent event) {
        System.out.println("RECEBEU EVENTO CREATE: " + event.getCategoryId());

        categoryQueryRepository.findById(event.getCategoryId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setCategoryName(event.getCategoryName());
                            categoryQueryRepository.save(existing);
                        },
                        () -> categoryQueryRepository.save(
                                new CategoryQueryEntity(
                                        event.getCategoryId(),
                                        event.getCategoryName()
                                )
                        )
                );
    }

    @KafkaListener(topics = "category-updated", groupId = "category-group")
    public void OnCategoriesUpdated(CategoryUpdateEvent event) {
        categoryQueryRepository.findById(event.getCategoryId())
                .ifPresentOrElse(
                        existing -> {
                            existing.setCategoryName(event.getCategoryName());
                            categoryQueryRepository.save(existing);
                        },
                        () -> categoryQueryRepository.save(
                                new CategoryQueryEntity(
                                        event.getCategoryId(),
                                        event.getCategoryName()
                                )
                        )
                );
    }

    @KafkaListener(topics = "category-deleted", groupId = "category-group")
    public void OnCategoriesDeleted(CategoryDeleteEvent event) {
        categoryQueryRepository.deleteById(event.getCategoryId());
    }
}
