package com.project.cqrs.query.product.service;

import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.product.dto.response.ProductQueryDTO;
import com.project.cqrs.query.product.repository.ProductQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductQueryService {

    private final ProductQueryRepository productRepository;

    public ProductQueryService(ProductQueryRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<ProductQueryDTO> findAll(Pageable pageable) {
        return productRepository.findByOrderByProductNameAsc(pageable)
                .map(ProductQueryDTO::from);
    }

    public ProductQueryDTO findById(Long id) {
        return productRepository.findById(id)
                .map(ProductQueryDTO::from)
                .orElseThrow(()-> new ResourceNotFoundException("Product not found" + id));
    }
}
