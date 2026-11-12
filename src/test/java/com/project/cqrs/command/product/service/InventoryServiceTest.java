package com.project.cqrs.command.product.service;
import com.project.cqrs.command.product.repository.ProductRepository;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import com.project.cqrs.command.product.kafka.producer.ProductEventProducer;
import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.order.dto.OrderItemDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class InventoryServiceTest {
    ProductCommandEntity product(long id, int stock) { return ProductCommandEntity.builder().productId(id).productName("Produto").productPrice(new BigDecimal("10.00")).stock(stock).categoryCommandEntity(CategoryCommandEntity.createCategory("Categoria")).build(); }
    @Test void mergesDuplicateItemsAndLocksInIdOrder() {
        var repo = mock(ProductRepository.class); var events = mock(ProductEventProducer.class); var p1 = product(1, 5); var p2 = product(2, 4);
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(p1)); when(repo.findByIdForUpdate(2L)).thenReturn(Optional.of(p2));
        var result = new InventoryService(repo, events).reserve(List.of(new OrderItemDTO(2L, 1), new OrderItemDTO(1L, 2), new OrderItemDTO(1L, 1)));
        var sequence = inOrder(repo); sequence.verify(repo).findByIdForUpdate(1L); sequence.verify(repo).findByIdForUpdate(2L);
        assertThat(p1.getStock()).isEqualTo(2); assertThat(p2.getStock()).isEqualTo(3); assertThat(result).hasSize(2);
        assertThat(result.get(0).getQuantity()).isEqualTo(3);
    }
    @Test void rejectsOverselling() {
        var repo = mock(ProductRepository.class); var events = mock(ProductEventProducer.class);
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(product(1, 1)));
        assertThatThrownBy(() -> new InventoryService(repo, events).reserve(List.of(new OrderItemDTO(1L, 2)))).isInstanceOf(com.project.cqrs.config.exception.InsufficientStockException.class);
        verifyNoInteractions(events);
    }
}