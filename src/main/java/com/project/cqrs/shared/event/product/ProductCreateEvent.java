package com.project.cqrs.shared.event.product;

import com.project.cqrs.command.product.model.ProductCommandEntity;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public final class  ProductCreateEvent extends ProductEvent {

    private String productName;
    private String productCode;
    private BigDecimal productPrice;
    private String productImage;
    private Long categoryId;

    //Constructors
    public ProductCreateEvent(Long productId, String productName, String productCode, BigDecimal productPrice, String productImage, Long categoryId) {
        super(productId);

        this.productName = productName;
        this.productCode = productCode;
        this.productPrice = productPrice;
        this.productImage = productImage;
        this.categoryId = categoryId;
    }

    protected ProductCreateEvent() {
        super();
        this.productName = null;
        this.productCode = null;
        this.productPrice = null;
        this.productImage = null;
        this.categoryId = null;
    }

    public static ProductCreateEvent fromEntity(ProductCommandEntity productEntity) {

        Long categoryId = null;


        if(productEntity.getCategoryCommandEntity() != null){

            categoryId =
                    productEntity
                            .getCategoryCommandEntity()
                            .getCategoryId();

        }
        return new ProductCreateEvent(
                productEntity.getProductId(),
                productEntity.getProductName(),
                productEntity.getProductCode(),
                productEntity.getProductPrice(),
                productEntity.getProductImage(),
                categoryId
        );
    }
}
