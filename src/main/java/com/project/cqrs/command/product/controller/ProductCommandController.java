package com.project.cqrs.command.product.controller;

import com.project.cqrs.command.product.dto.request.CreateProductRequestDTO;
import com.project.cqrs.command.product.dto.request.UpdateProductRequestDTO;
import com.project.cqrs.command.product.service.ProductCommandService;
import com.project.cqrs.config.rateLimit.RateLimit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/command/products")
public class ProductCommandController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.project.cqrs.command.product.service.InventoryService inventory;
    public record StockRequest(@jakarta.validation.constraints.Positive int quantity) {}
    @PostMapping("/{id}/stock")
    public ResponseEntity<Void> replenish(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        inventory.replenish(id, request.quantity());
        return ResponseEntity.noContent().build();
    }
    private final ProductCommandService productCommandService;
    public ProductCommandController(ProductCommandService productCommandService) {
        this.productCommandService = productCommandService;

    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @PostMapping
    public ResponseEntity<Void> createProduct(@Valid @RequestBody CreateProductRequestDTO productDTO) {
        productCommandService.createProduct(productDTO);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProduct(@Valid @PathVariable Long id, @Valid @RequestBody UpdateProductRequestDTO requestDTO) {
        productCommandService.updateProduct(id, requestDTO);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productCommandService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
