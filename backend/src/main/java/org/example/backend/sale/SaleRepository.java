package org.example.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // receipt_number is a plain read here (unaffected by insertable/updatable
    // = false, which only blocks writes), so a derived query works fine.
    Optional<Sale> findByReceiptNumber(Long receiptNumber);

    List<Sale> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant from);

    List<Sale> findAllByOrderByCreatedAtDesc();

    // Day close's closed range, unlike the open-ended "since" query above —
    // a picked day has both a start and an end, today doesn't.
    List<Sale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from, Instant to);
}
