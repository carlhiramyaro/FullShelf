package org.example.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import org.example.backend.shop.Shop;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Every lookup carries the shop: receipt numbers repeat across shops, and
    // a query that cannot return another shop's row cannot leak it.
    Optional<Sale> findByShopAndReceiptNumber(Shop shop, Long receiptNumber);

    List<Sale> findByShopAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Shop shop, Instant from);

    List<Sale> findByShopOrderByCreatedAtDesc(Shop shop);

    // Day close's closed range, unlike the open-ended "since" query above —
    // a picked day has both a start and an end, today doesn't.
    List<Sale> findByShopAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Shop shop, Instant from, Instant to);
}
