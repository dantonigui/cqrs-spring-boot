package com.project.cqrs.query.category.dto.response;


import com.project.cqrs.query.category.model.CategoryQueryEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryQueryDTO(

        @NotNull(message = "The category id is required")
        Long categoryId,

        @NotBlank(message = "The category name must be longer than 4 characters")
        @Size(min = 4)
        String categoryName
) {
    public static CategoryQueryDTO from(CategoryQueryEntity categoryQueryEntity) {
        return new CategoryQueryDTO(
                categoryQueryEntity.getCategoryId(),
                categoryQueryEntity.getCategoryName()
        );
    }
}
