package com.project.cqrs.command.product.model;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Table(name = "product_command")
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal productPrice;

    @Column(nullable = false)
    private String productImage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category", nullable = false)
    private CategoryCommandEntity categoryCommandEntity;

    @Builder.Default
    @Column(nullable = false)
    private Integer stock = 0;

    public void reserveStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva");
        if (stock < quantity) throw new com.project.cqrs.config.exception.InsufficientStockException(productId);
        stock -= quantity;
    }
    public void replenishStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva");
        stock = Math.addExact(stock, quantity);
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
