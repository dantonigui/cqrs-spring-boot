package com.project.cqrs.command.product.repository;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface ProductRepository extends JpaRepository<ProductCommandEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductCommandEntity p where p.productId = :id")
    Optional<ProductCommandEntity> findByIdForUpdate(@Param("id") Long id);
}