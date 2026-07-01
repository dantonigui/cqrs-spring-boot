package com.project.cqrs.query.product.controller;

import com.project.cqrs.config.rateLimit.RateLimit;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.query.product.service.ProductQueryService;
import com.project.cqrs.shared.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("api/v1/query/products")
@Tag(name="Products", description = "Endpoints Products")
public class ProductQueryController {

    private final ProductQueryService productQueryService;

    public ProductQueryController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @RateLimit(requests = 100, durationSeconds = 30)
    @GetMapping
    @Operation(summary = "Search All Products")
    @ApiResponse(responseCode = "200", description = "Products Found")
    @ApiResponse(responseCode = "404", description = "Not Found Products")
    public ResponseEntity<PageResponseDTO<ProductQueryDTO>> findAll(@PageableDefault(size = 15) Pageable pageable) {
        return ResponseEntity.ok(productQueryService.findAll(pageable));
    }

    @RateLimit(requests = 100, durationSeconds = 30)
    @GetMapping("/{id}")
    @Operation(summary = "Search products for ID")
    @ApiResponse(responseCode = "200", description = "Product Found")
    @ApiResponse(responseCode = "404", description = "Not Found Product Id")
    public ResponseEntity<ProductQueryDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productQueryService.findById(id));
    }
}
