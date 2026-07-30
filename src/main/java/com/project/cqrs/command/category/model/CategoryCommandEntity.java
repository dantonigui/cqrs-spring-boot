package com.project.cqrs.command.category.model;

import com.project.cqrs.command.product.model.ProductCommandEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "category_command")
public class CategoryCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @Column(nullable = false)
    private String categoryName;

    @OneToMany(mappedBy = "categoryCommandEntity", cascade = CascadeType.ALL)
    private List<ProductCommandEntity> products;

    private CategoryCommandEntity(String categoryName) {
        this.categoryName = categoryName;
    }

    // Factory Method
    public static CategoryCommandEntity createCategory(String categoryName) {
        return CategoryCommandEntity.builder()
                .categoryName(categoryName)
                .build();
    }

    //Setters
    public void updateCategory(String categoryName) {
        this.categoryName = categoryName;
    }
}
