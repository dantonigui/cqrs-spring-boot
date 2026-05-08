package com.project.cqrs.command.category.repository;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryCommandEntity, Long> {
}
