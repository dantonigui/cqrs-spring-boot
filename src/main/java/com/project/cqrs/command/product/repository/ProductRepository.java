package com.project.cqrs.command.product.repository;

import com.project.cqrs.command.product.model.ProductCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductCommandEntity, Long> {
}
