package org.example.backend.product;

import org.example.backend.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every lookup takes the shop, so a forgotten check can't quietly read another
 * shop's catalog. There is deliberately no unscoped list or exists method.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShopOrderByName(Shop shop);

    Optional<Product> findByIdAndShop(Long id, Shop shop);

    boolean existsByShopAndNameIgnoreCase(Shop shop, String name);

    boolean existsByShopAndNameIgnoreCaseAndIdNot(Shop shop, String name, Long id);
}
