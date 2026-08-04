package com.project.cqrs.shared.event.category;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import lombok.Getter;

@Getter
public final class CategoryCreateEvent extends CategoryEvent {

    private final String categoryName;

    public CategoryCreateEvent(Long categoryId, String categoryName) {
        super(categoryId);
        this.categoryName = categoryName;
    }

    protected CategoryCreateEvent() {
        super();
        this.categoryName = null;
    }

    public static CategoryCreateEvent fromEntity(CategoryCommandEntity entity) {
        return new CategoryCreateEvent(
                entity.getCategoryId(),
                entity.getCategoryName()
        );
    }
}