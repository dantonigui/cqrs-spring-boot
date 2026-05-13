package com.project.cqrs.command.category.service;

import com.project.cqrs.command.category.dto.request.CreateCategoryRequestDTO;
import com.project.cqrs.command.category.dto.request.UpdateCategoryRequestDTO;
import com.project.cqrs.command.category.event.CategoryCreateEvent;
import com.project.cqrs.command.category.event.CategoryUpdateEvent;
import com.project.cqrs.command.category.kafka.producer.CategoryEventProducer;
import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.category.repository.CategoryRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryCommandServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryEventProducer categoryEventProducer;

    @InjectMocks
    private CategoryCommandService categoryCommandService;

    private CategoryCommandEntity categoryCommandEntity;

    @BeforeEach
    void setUp() {

        categoryCommandEntity =
                CategoryCommandEntity.createCategory("Electronics");

        ReflectionTestUtils.setField(
                categoryCommandEntity,
                "categoryId",
                1L
        );
    }

    // =========================================================================================
    // createCategory
    // =========================================================================================

    @Test
    @DisplayName("createCategory: deve salvar a categoria e publicar evento")
    void createCategory_success() {
        CreateCategoryRequestDTO dto = new CreateCategoryRequestDTO("Electronics");

        when(categoryRepository.save(any(CategoryCommandEntity.class))).thenReturn(categoryCommandEntity);

        categoryCommandService.createCategory(dto);

        verify(categoryRepository, times(1)).save(any(CategoryCommandEntity.class));
        verify(categoryEventProducer, times(1)).sendCategoryCreated(any(), any(CategoryCreateEvent.class));
    }

    // =========================================================================================
    // updateCategory
    // =========================================================================================

    @Test
    @DisplayName("updateCategory: deve atualizar a categoria e publicar evento")
    void updateCategory_success() {
        UpdateCategoryRequestDTO dto = mock(UpdateCategoryRequestDTO.class);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(categoryCommandEntity));
        when(categoryRepository.save(any(CategoryCommandEntity.class))).thenReturn(categoryCommandEntity);

        categoryCommandService.updateCategory(dto, 1L);

        verify(dto,times(1)).applyTo(categoryCommandEntity);
        verify(categoryRepository, times(1)).save(any(CategoryCommandEntity.class));
        verify(categoryEventProducer, times(1)).sendCategoryUpdated(any(), any(CategoryUpdateEvent.class));
    }

    @Test
    @DisplayName("updateCategory: deve lançar ResourceNotFoundException quando categoria não existe")
    void updateCategory_resourceNotFoundException() {
        UpdateCategoryRequestDTO dto = mock(UpdateCategoryRequestDTO.class);

        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryCommandService.updateCategory(dto,99L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(categoryRepository, never()).save(any());
        verify(categoryEventProducer, never()).sendCategoryUpdated(any(), any(CategoryUpdateEvent.class));
    }

    // ===================================================================================================
    // deleteCategory
    // ===================================================================================================

    @Test
    @DisplayName("deleteCategoryById: deve deletar a categoria e publicar evento")
    void deleteCategory_success() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(categoryCommandEntity));

        categoryCommandService.deleteCategoryById(1L);

        verify(categoryRepository, times(1)).delete(categoryCommandEntity);
        verify(categoryEventProducer, times(1)).sendCategoryDeleted(any(), any());
    }

    @Test
    @DisplayName("deleteCategoryById: deve lançar ResourceNotFoundException quando categoria não existe")
    void deleteCategory_notFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> categoryCommandService.deleteCategoryById(1L))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("1");

        verify(categoryRepository, never()).delete(any());
        verify(categoryEventProducer, never()).sendCategoryDeleted(any(), any());
    }
}
