package org.example.backend.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    List<SaleLine> findBySaleId(Long saleId);

    // Sale has no persisted total (same "no cached derived column" call as
    // Product's balance) — the sales list needs it per row, so it's summed
    // from sale_lines on the fly rather than stored on Sale at confirm time.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l where l.sale.id = :saleId")
    BigDecimal sumLineTotalBySaleId(@Param("saleId") Long saleId);

    // Dashboard's "expected cash" — lineTotal is already net of discount
    // (SaleService.resolveLine), so this sum doubles as expected cash with
    // no separate calculation. Voided sales are excluded: Void's own
    // contract is "expected cash reduced," so a voided line must not count
    // here even though the row itself stays visible everywhere else.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l "
            + "where l.sale.createdAt >= :from and l.sale.voided = false")
    BigDecimal sumLineTotalSince(@Param("from") Instant from);

    // Dashboard's "discounts today." Same voided exclusion as sumLineTotalSince.
    @Query("select coalesce(sum(l.discount), 0) from SaleLine l "
            + "where l.sale.createdAt >= :from and l.sale.voided = false")
    BigDecimal sumDiscountSince(@Param("from") Instant from);

    // Day close's totals: a closed range instead of "since now," so a picked
    // day's totals don't include everything after it. Same voided exclusion.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l "
            + "where l.sale.createdAt >= :from and l.sale.createdAt < :to and l.sale.voided = false")
    BigDecimal sumLineTotalBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(l.discount), 0) from SaleLine l "
            + "where l.sale.createdAt >= :from and l.sale.createdAt < :to and l.sale.voided = false")
    BigDecimal sumDiscountBetween(@Param("from") Instant from, @Param("to") Instant to);

    // Day close's per-sale discount column — SaleSummary deliberately doesn't
    // carry this (see Dashboard's decision doc: widening it would touch two
    // consumers that don't need it), so Day close sums it itself per sale,
    // same shape as sumLineTotalBySaleId.
    @Query("select coalesce(sum(l.discount), 0) from SaleLine l where l.sale.id = :saleId")
    BigDecimal sumDiscountBySaleId(@Param("saleId") Long saleId);
}
