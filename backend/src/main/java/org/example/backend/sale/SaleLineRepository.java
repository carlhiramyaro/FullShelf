package org.example.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    List<SaleLine> findBySaleId(Long saleId);

    // Sale has no persisted total (same "no cached derived column" call as
    // Product's balance) — the sales list needs it per row, so it's summed
    // from sale_lines on the fly rather than stored on Sale at confirm time.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l where l.sale.id = :saleId")
    BigDecimal sumLineTotalBySaleId(@Param("saleId") Long saleId);
}
