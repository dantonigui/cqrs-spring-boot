package com.project.cqrs.query.product.service;

import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductQueryService {

    private final ProductQueryRepository productRepository;

    public ProductQueryService(ProductQueryRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductQueryDTO> findAll() {
        return productRepository.findByOrderByProductNameAsc()
                .stream()
                .map(ProductQueryDTO::from)
                .toList();
    }

    public ProductQueryDTO findById(Long id) {
        return productRepository.findById(id)
                .map(ProductQueryDTO::from)
                .orElseThrow(()-> new RuntimeException("product not found"));
    }
}
