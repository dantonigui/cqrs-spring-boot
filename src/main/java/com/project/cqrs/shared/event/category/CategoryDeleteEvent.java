package com.project.cqrs.shared.event.category;


import com.project.cqrs.command.category.model.CategoryCommandEntity;
import lombok.Getter;

@Getter
public final class CategoryDeleteEvent extends CategoryEvent {

    private Long categoryId;

    public CategoryDeleteEvent(Long categoryId) {
        super(categoryId);
    }

    public CategoryDeleteEvent() {}

    public static CategoryDeleteEvent fromEntity(CategoryCommandEntity entity) {
        return new CategoryDeleteEvent(entity.getCategoryId());
    }

}
