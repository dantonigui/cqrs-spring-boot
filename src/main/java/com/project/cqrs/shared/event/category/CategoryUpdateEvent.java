package com.project.cqrs.shared.event.category;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Setter
@Getter
public final class CategoryUpdateEvent extends CategoryEvent{

    private Long categoryId;
    private String categoryName;

    public CategoryUpdateEvent(Long categoryId, String categoryName) {
        super(categoryId);
        this.categoryName = categoryName;
    }

    public CategoryUpdateEvent() {}

    public static CategoryUpdateEvent fromEntity(CategoryCommandEntity categoryCommandEntity) {
        return new CategoryUpdateEvent(
                categoryCommandEntity.getCategoryId(),
                categoryCommandEntity.getCategoryName());
    }

}
