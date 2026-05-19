package com.project.cqrs.command.product.service;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import com.project.cqrs.command.product.dto.request.CreateProductRequestDTO;
import com.project.cqrs.command.product.dto.request.UpdateProductRequestDTO;
import com.project.cqrs.shared.event.product.ProductCreateEvent;
import com.project.cqrs.shared.event.product.ProductDeleteEvent;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
import com.project.cqrs.command.product.kafka.producer.ProductEventProducer;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import com.project.cqrs.command.product.repository.ProductRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductCommandServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductEventProducer productEventProducer;

    @InjectMocks
    private ProductCommandService productCommandService;

    private ProductCommandEntity productCommandEntity;
    private CategoryCommandEntity category;

    @BeforeEach
    void setup() {

        category = CategoryCommandEntity.createCategory("Foods");

        productCommandEntity = ProductCommandEntity.createProduct(
                "Rice",
                "0001",
                new BigDecimal("11.00"),
                "www.image.com",
                category
        );

        // Simula ID gerado pelo banco
        ReflectionTestUtils.setField(productCommandEntity, "productId", 1L);
    }

    // ===============================================================
    // createProduct
    // ===============================================================

    @Test
    @DisplayName("createProduct: Deve salvar o produto e publicar o evento")
    void createProduct_Success() {

        CreateProductRequestDTO requestDTO =
                new CreateProductRequestDTO(
                        "Mouse",
                        "MOU123",
                        BigDecimal.valueOf(100),
                        "image.png",
                        1L
                );

        ProductCommandEntity savedProduct =
                ProductCommandEntity.createProduct(
                        "Mouse",
                        "MOU123",
                        BigDecimal.valueOf(100),
                        "image.png",
                        category
                );

        ReflectionTestUtils.setField(savedProduct, "productId", 1L);

        when(categoryRepository.findById(anyLong()))
                .thenReturn(Optional.of(category));

        when(productRepository.saveAndFlush(any(ProductCommandEntity.class)))
                .thenReturn(savedProduct);

        productCommandService.createProduct(requestDTO);

        verify(categoryRepository, times(1))
                .findById(1L);

        verify(productRepository, times(1))
                .saveAndFlush(any(ProductCommandEntity.class));

        verify(productEventProducer, times(1))
                .sendProductCreated(eq("1"), any(ProductCreateEvent.class));
    }

    @Test
    @DisplayName("createProduct: Deve lançar erro quando categoria não existir")
    void createProduct_CategoryNotFound() {

        CreateProductRequestDTO requestDTO =
                new CreateProductRequestDTO(
                        "Mouse",
                        "MOU123",
                        BigDecimal.valueOf(100),
                        "image.png",
                        99L
                );

        when(categoryRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productCommandService.createProduct(requestDTO)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never())
                .saveAndFlush(any());

        verify(productEventProducer, never())
                .sendProductCreated(any(), any());
    }

    // ===============================================================
    // updateProduct
    // ===============================================================

    @Test
    @DisplayName("updateProduct: Deve atualizar o produto e publicar o evento")
    void updateProduct_Success() {

        Long productId = 1L;

        UpdateProductRequestDTO dto =
                new UpdateProductRequestDTO(
                        "Keyboard",
                        "KEY001",
                        BigDecimal.valueOf(200),
                        "keyboard.png",
                        1L
                );

        when(productRepository.findById(productId))
                .thenReturn(Optional.of(productCommandEntity));

        when(categoryRepository.findById(anyLong()))
                .thenReturn(Optional.of(category));

        when(productRepository.save(any(ProductCommandEntity.class)))
                .thenReturn(productCommandEntity);

        productCommandService.updateProduct(productId, dto);

        verify(productRepository, times(1))
                .findById(productId);

        verify(categoryRepository, times(1))
                .findById(1L);

        verify(productRepository, times(1))
                .save(any(ProductCommandEntity.class));

        verify(productEventProducer, times(1))
                .sendProductUpdated(eq("1"), any(ProductUpdateEvent.class));
    }

    @Test
    @DisplayName("updateProduct: Deve lançar erro quando produto não existir")
    void updateProduct_ProductNotFound() {

        Long productId = 99L;

        UpdateProductRequestDTO dto =
                new UpdateProductRequestDTO(
                        "Keyboard",
                        "KEY001",
                        BigDecimal.valueOf(200),
                        "keyboard.png",
                        1L
                );

        when(productRepository.findById(productId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productCommandService.updateProduct(productId, dto)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never())
                .save(any());

        verify(productEventProducer, never())
                .sendProductUpdated(any(), any());
    }

    // ===============================================================
    // deleteProduct
    // ===============================================================

    @Test
    @DisplayName("deleteProduct: Deve deletar o produto e publicar o evento")
    void deleteProduct_Success() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(productCommandEntity));

        productCommandService.deleteProduct(1L);

        verify(productRepository, times(1))
                .findById(1L);

        verify(productRepository, times(1))
                .delete(productCommandEntity);

        verify(productEventProducer, times(1))
                .sendProductDeleted(eq("1"), any(ProductDeleteEvent.class));
    }

    @Test
    @DisplayName("deleteProduct: Deve lançar erro quando produto não existir")
    void deleteProduct_NotFound() {

        Long productId = 99L;

        when(productRepository.findById(productId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productCommandService.deleteProduct(productId)
        )
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(productRepository, never())
                .delete(any());

        verify(productEventProducer, never())
                .sendProductDeleted(any(), any());
    }
}