package com.project.cqrs.shared.event.category;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public final class CategoryUpdateEvent extends CategoryEvent {

    private String categoryName;

    public CategoryUpdateEvent(
            Long categoryId,
            String categoryName
    ) {
        super(categoryId);
        this.categoryName = categoryName;
    }

    public static CategoryUpdateEvent fromEntity(CategoryCommandEntity entity) {
        return new CategoryUpdateEvent(
                entity.getCategoryId(),
                entity.getCategoryName()
        );
    }
}