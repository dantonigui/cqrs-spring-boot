package com.project.cqrs.command.category.controller;


import com.project.cqrs.command.category.dto.request.CreateCategoryRequestDTO;
import com.project.cqrs.command.category.dto.request.UpdateCategoryRequestDTO;
import com.project.cqrs.command.category.service.CategoryCommandService;
import com.project.cqrs.config.rateLimit.RateLimit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/command/categories")
public class CategoryController {

    private final CategoryCommandService categoryCommandService;
    public CategoryController(CategoryCommandService categoryCommandService) {
        this.categoryCommandService = categoryCommandService;
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @PostMapping
    public ResponseEntity<Void> createCategory(@Valid @RequestBody CreateCategoryRequestDTO categoryRequestDTO) {
        categoryCommandService.createCategory(categoryRequestDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequestDTO categoryRequestDTO) {
        categoryCommandService.updateCategory(categoryRequestDTO, id);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryCommandService.deleteCategoryById(id);

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
