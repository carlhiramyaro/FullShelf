package org.example.backend.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {

    Optional<ProductPriceHistory> findFirstByProductOrderByChangedAtDescIdDesc(Product product);
}
