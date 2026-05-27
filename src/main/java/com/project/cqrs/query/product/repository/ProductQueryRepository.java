package com.project.cqrs.query.product.repository;

import com.project.cqrs.query.product.model.ProductQueryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductQueryRepository extends JpaRepository<ProductQueryEntity, Long> {

    Page<ProductQueryEntity> findByOrderByProductNameAsc(Pageable pageable);

}
