package com.project.cqrs.command.product.model;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "product_command")
public class ProductCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private BigDecimal productPrice;

    @Column(nullable = false)
    private String productImage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category", nullable = false)
    private CategoryCommandEntity categoryCommandEntity;

    //Constructor
    protected  ProductCommandEntity() {
    }

    @Builder
    private ProductCommandEntity(String productName,
                          String productCode,
                          BigDecimal productPrice,
                          String productImage,
                          CategoryCommandEntity categoryCommandEntity) {

        this.productName = productName;
        this.productCode = productCode;
        this.productPrice = productPrice;
        this.productImage = productImage;
        this.categoryCommandEntity = categoryCommandEntity;
    }

    // Factory Method
    public static ProductCommandEntity createProduct(String productName,
                                              String productCode,
                                              BigDecimal productPrice,
                                              String productImage,
                                              CategoryCommandEntity categoryCommandEntity) {

        return ProductCommandEntity.builder()
                .productName(productName)
                .productCode(productCode)
                .productPrice(productPrice)
                .productImage(productImage)
                .categoryCommandEntity(categoryCommandEntity)
                .build();
    }

    //Setters
    public void updateProductName(String productName) {
        this.productName = productName;
    }
    public void updateProductCode(String productCode) {
        this.productCode = productCode;
    }
    public void updateProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }
    public void updateProductImage(String productImage) {
        this.productImage = productImage;
    }
    public void updateProductCategory(CategoryCommandEntity categoryCommandEntity) {
        this.categoryCommandEntity = categoryCommandEntity;
    }

}
