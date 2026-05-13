package com.project.cqrs.query.category.service;

import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.category.repository.CategoryQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryQueryService {

    private final CategoryQueryRepository categoryQueryRepository;

    public CategoryQueryService(CategoryQueryRepository categoryQueryRepository) {
        this.categoryQueryRepository = categoryQueryRepository;
    }

    public List<CategoryQueryDTO> findAllCategories() {
        return categoryQueryRepository.findByOrderByCategoryNameAsc().stream().map(CategoryQueryDTO::from).toList();
    }

    public CategoryQueryDTO findById(Long id) {
        return categoryQueryRepository.findById(id).map(CategoryQueryDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id " + id + " not found"));
    }
}
