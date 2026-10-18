package org.example.backend.stock;

import org.example.backend.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @Query("select sum(m.quantity) from StockMovement m where m.product.id = :productId")
    Optional<BigDecimal> sumQuantityByProductId(@Param("productId") Long productId);

    /** One row per product that has any movement: {productId, sum}. Scoped by the movement's own shop_id. */
    @Query("select m.product.id, sum(m.quantity) from StockMovement m where m.shop = :shop group by m.product.id")
    List<Object[]> sumQuantityByProductForShop(@Param("shop") Shop shop);

    /** One row per product with at least one unreversed movement of the given types: {productId, count}. */
    @Query("""
            select m.product.id, count(m) from StockMovement m
            where m.shop = :shop and m.type in :types
              and not exists (select r.id from StockMovement r where r.reversedMovement = m)
            group by m.product.id
            """)
    List<Object[]> countUnreversedByProductForShop(@Param("shop") Shop shop,
                                                    @Param("types") Collection<StockMovementType> types);

    List<StockMovement> findByProductIdOrderByCreatedAtAsc(Long productId);

    List<StockMovement> findByProductIdAndType(Long productId, StockMovementType type);

    List<StockMovement> findByProductIdAndTypeInOrderByCreatedAtDesc(Long productId, Collection<StockMovementType> types);

    Optional<StockMovement> findByIdAndShop(Long id, Shop shop);

    List<StockMovement> findBySaleLineId(Long saleLineId);

    boolean existsByReversedMovementId(Long reversedMovementId);
}
