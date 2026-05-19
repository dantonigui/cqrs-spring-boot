package com.project.cqrs.shared.event.category;

import com.project.cqrs.command.category.model.CategoryCommandEntity;


public class CategoryCreateEvent {

    private Long categoryId;
    private String categoryName;

    public CategoryCreateEvent(Long categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public CategoryCreateEvent() {}

    public static CategoryCreateEvent fromEntity(CategoryCommandEntity categoryEntity) {
        return  new CategoryCreateEvent(
                categoryEntity.getCategoryId(),
                categoryEntity.getCategoryName()
        );
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }
}
