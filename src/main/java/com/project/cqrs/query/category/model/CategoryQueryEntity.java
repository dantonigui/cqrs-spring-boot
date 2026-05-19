package com.project.cqrs.query.category.model;

import jakarta.persistence.*;

@Entity
@Table(name = "category_query")
public class CategoryQueryEntity {

    @Id
    private Long categoryId;

    private String categoryName;

    protected CategoryQueryEntity() {}

    public CategoryQueryEntity(Long categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
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
