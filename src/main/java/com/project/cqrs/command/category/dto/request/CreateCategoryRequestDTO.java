package com.project.cqrs.command.category.dto.request;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequestDTO(
        @NotBlank
        @Size(min = 4)
        String categoryName
) {
    public CategoryCommandEntity toEntity() {
        return CategoryCommandEntity.createCategory(categoryName);
    }
}
