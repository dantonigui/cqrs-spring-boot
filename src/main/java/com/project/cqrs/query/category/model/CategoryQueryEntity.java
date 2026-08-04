package com.project.cqrs.query.category.model;

import com.project.cqrs.shared.event.category.CategoryEvent;
import com.project.cqrs.shared.event.category.CategoryUpdateEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category_query")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryQueryEntity {

    @Id
    private Long categoryId;


    private String categoryName;

    public static CategoryQueryEntity fromcreateEvent(Long categoryId, String categoryName) {
        return CategoryQueryEntity.builder()
                .categoryId(categoryId)
                .categoryName(categoryName)
                .build();
    }

    public  void appyUpdateEvent(CategoryUpdateEvent categoryEvent) {
        this.categoryId = categoryEvent.getCategoryId();
        this.categoryName = categoryEvent.getCategoryName();
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
