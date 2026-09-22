package org.example.backend.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select sum(m.quantity) from StockMovement m where m.product.id = :productId")
    Optional<BigDecimal> sumQuantityByProductId(@Param("productId") Long productId);

    List<StockMovement> findByProductIdOrderByCreatedAtAsc(Long productId);

    List<StockMovement> findBySaleLineId(Long saleLineId);

    boolean existsByReversedMovementId(Long reversedMovementId);
}
