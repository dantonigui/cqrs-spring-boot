package com.project.cqrs.query.category.repository;

import com.project.cqrs.query.category.model.CategoryQueryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryQueryRepository extends JpaRepository<CategoryQueryEntity,Long> {

    Page<CategoryQueryEntity> findByOrderByCategoryNameAsc(Pageable pageable);
}
