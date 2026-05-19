package com.project.cqrs.shared.event.category;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import org.springframework.stereotype.Component;

@Component
public class CategoryUpdateEvent {

    private Long categoryId;
    private String categoryName;

    public CategoryUpdateEvent(Long categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public CategoryUpdateEvent() {}

    public static CategoryUpdateEvent fromEntity(CategoryCommandEntity categoryCommandEntity) {
        return new CategoryUpdateEvent(
                categoryCommandEntity.getCategoryId(),
                categoryCommandEntity.getCategoryName());
    }

    public Long getCategoryId() {
        return categoryId;
    }
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}
