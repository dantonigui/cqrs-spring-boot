package com.project.cqrs.query.category.service;

import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.category.repository.CategoryQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryQueryService {

    private final CategoryQueryRepository categoryQueryRepository;

    public CategoryQueryService(CategoryQueryRepository categoryQueryRepository) {
        this.categoryQueryRepository = categoryQueryRepository;
    }

    @Transactional(readOnly = true)
    public Page<CategoryQueryDTO> findAllCategories(Pageable  pageable) {
        return categoryQueryRepository.findByOrderByCategoryNameAsc(pageable)
                .map(CategoryQueryDTO::from);
    }

    @Transactional(readOnly = true)
    public CategoryQueryDTO findById(Long id) {
        return categoryQueryRepository.findById(id).map(CategoryQueryDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id " + id + " not found"));
    }
}
