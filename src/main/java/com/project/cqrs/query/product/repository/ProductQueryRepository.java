package com.project.cqrs.query.product.repository;

import com.project.cqrs.query.product.model.ProductQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductQueryRepository extends JpaRepository<ProductQueryEntity, Long> {

    List<ProductQueryEntity> findByOrderByProductNameAsc();

}
