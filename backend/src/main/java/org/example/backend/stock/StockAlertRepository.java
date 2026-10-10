package org.example.backend.stock;

import org.example.backend.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {

    // The alert's primary key is its product, so this is the same row as
    // findById, with the shop in the query like every other G3-G5 lookup.
    Optional<StockAlert> findByProductIdAndShop(Long productId, Shop shop);
}
