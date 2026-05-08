package com.project.cqrs.command.category.service;

import com.project.cqrs.command.category.dto.request.CreateCategoryRequestDTO;
import com.project.cqrs.command.category.dto.request.UpdateCategoryRequestDTO;
import com.project.cqrs.command.category.event.CategoryCreateEvent;
import com.project.cqrs.command.category.event.CategoryDeleteEvent;
import com.project.cqrs.command.category.event.CategoryUpdateEvent;
import com.project.cqrs.command.category.kafka.producer.CategoryEventProducer;
import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class CategoryCommandService {

    private  final CategoryRepository categoryRepository;
    private final CategoryEventProducer categoryEventProducer;
    public CategoryCommandService(CategoryRepository categoryRepository, CategoryEventProducer categoryEventProducer) {
        this.categoryRepository = categoryRepository;
        this.categoryEventProducer = categoryEventProducer;
    }



    public void createCategory(CreateCategoryRequestDTO requestDTO) {
        CategoryCommandEntity categoryEntity = CategoryCommandEntity.createCategory(requestDTO.categoryName());

        categoryRepository.save(categoryEntity);

        CategoryCreateEvent event = CategoryCreateEvent.fromEntity(categoryEntity);
        categoryEventProducer.sendCategoryCreated(categoryEntity.getCategoryId().toString(), event);
    }

    public void deleteCategoryById(Long id) {

        CategoryCommandEntity category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Category with id " + id + " not found"));

        CategoryDeleteEvent event = CategoryDeleteEvent.fromEntity(category);

        categoryRepository.delete(category);

        categoryEventProducer.sendCategoryDeleted(id.toString(), event);
    }

    public void updateCategory(UpdateCategoryRequestDTO requestDTO, Long id) {
        CategoryCommandEntity categoryCommandEntity = categoryRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Category with id " + id + " not found"));

        requestDTO.applyTo(categoryCommandEntity);

        CategoryUpdateEvent event = CategoryUpdateEvent.fromEntity(categoryCommandEntity);
        categoryEventProducer.sendCategoryUpdated(categoryCommandEntity.getCategoryId().toString(), event);
    }
}
