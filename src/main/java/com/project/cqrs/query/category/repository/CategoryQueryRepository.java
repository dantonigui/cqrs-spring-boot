package com.project.cqrs.query.category.repository;

import com.project.cqrs.query.category.model.CategoryQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryQueryRepository extends JpaRepository<CategoryQueryEntity,Long> {

    List<CategoryQueryEntity> findByOrderByCategoryNameAsc();
}
