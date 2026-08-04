package com.project.cqrs.shared.event.category;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public final class CategoryCreateEvent extends CategoryEvent {

    private Long categoryId;
    private String categoryName;

    public CategoryCreateEvent(Long categoryId, String categoryName) {
        super(categoryId);
        this.categoryName = categoryName;
    }

    private CategoryCreateEvent() {}

    public static CategoryCreateEvent fromEntity(CategoryCommandEntity categoryEntity) {
        return  new CategoryCreateEvent(
                categoryEntity.getCategoryId(),
                categoryEntity.getCategoryName()
        );
    }

}
