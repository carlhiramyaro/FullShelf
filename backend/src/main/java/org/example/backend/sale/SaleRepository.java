package org.example.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // receipt_number is DB-assigned (see Sale.receiptNumber) and invisible on
    // the just-saved entity until re-read. A plain findById would return the
    // same managed instance from the persistence context instead of hitting
    // the DB, so this goes around the first-level cache with a native query.
    @Query(value = "select receipt_number from sales where id = :id", nativeQuery = true)
    Long findReceiptNumberById(@Param("id") Long id);

    // receipt_number is a plain read here (unaffected by insertable/updatable
    // = false, which only blocks writes), so a derived query works fine.
    Optional<Sale> findByReceiptNumber(Long receiptNumber);

    List<Sale> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant from);

    List<Sale> findAllByOrderByCreatedAtDesc();
}
