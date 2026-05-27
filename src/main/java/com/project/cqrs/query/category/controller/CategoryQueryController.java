package com.project.cqrs.query.category.controller;


import com.project.cqrs.config.rateLimit.RateLimit;
import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.category.service.CategoryQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/query/categories")
public class CategoryQueryController {

    private final CategoryQueryService categoryQueryService;

    public CategoryQueryController(CategoryQueryService categoryQueryService) {
        this.categoryQueryService = categoryQueryService;
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @GetMapping
    public ResponseEntity<Page<CategoryQueryDTO>> getCategories(@PageableDefault Pageable pageable) {
        return ResponseEntity.ok(categoryQueryService.findAllCategories(pageable));
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @GetMapping("/{id}")
    public ResponseEntity<CategoryQueryDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryQueryService.findById(id));
    }
}
