package com.project.cqrs.query.category.controller;


import com.project.cqrs.query.category.dto.response.CategoryQueryDTO;
import com.project.cqrs.query.category.service.CategoryQueryService;
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

    @GetMapping
    public ResponseEntity<List<CategoryQueryDTO>> getCategories() {
        return ResponseEntity.ok(categoryQueryService.findAllCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryQueryDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryQueryService.findById(id));
    }
}
