package com.project.cqrs.query.product.controller;

import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.query.product.service.ProductQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/query/products")
public class ProductQueryController {

    private final ProductQueryService productQueryService;

    public ProductQueryController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping
    public ResponseEntity<List<ProductQueryDTO>> findAll() {
        return ResponseEntity.ok(productQueryService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductQueryDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productQueryService.findById(id));
    }
}
