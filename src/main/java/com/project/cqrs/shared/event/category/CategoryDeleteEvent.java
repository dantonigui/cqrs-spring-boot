package com.project.cqrs.shared.event.category;


import com.project.cqrs.command.category.model.CategoryCommandEntity;

public class CategoryDeleteEvent {

    private Long categoryId;

    public CategoryDeleteEvent(Long categoryId) {
        this.categoryId = categoryId;
    }

    public CategoryDeleteEvent() {}

    public static CategoryDeleteEvent fromEntity(CategoryCommandEntity entity) {
        return new CategoryDeleteEvent(entity.getCategoryId());
    }

    public Long getCategoryId() {
        return categoryId;
    }
}
