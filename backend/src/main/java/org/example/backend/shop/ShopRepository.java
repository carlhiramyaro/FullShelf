package org.example.backend.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findFirstByOrderByIdAsc();

    // Hands out the shop's next receipt number and advances the counter in one
    // statement. The UPDATE takes a row lock, so two sales in the same shop
    // serialize here and can never get the same number; sales in different
    // shops touch different rows and don't wait on each other. A rolled-back
    // sale rolls the increment back too, so numbers never skip.
    @Query(value = "update shops set next_receipt_number = next_receipt_number + 1 "
            + "where id = :shopId returning next_receipt_number - 1", nativeQuery = true)
    Long nextReceiptNumber(@Param("shopId") Long shopId);
}
