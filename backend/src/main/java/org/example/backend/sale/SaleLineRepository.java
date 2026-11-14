package org.example.backend.sale;

import org.example.backend.shop.Shop;
import org.example.backend.product.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    List<SaleLine> findBySaleId(Long saleId);

    /**
     * The one-time fill (I2): when a product gets its first cost, its old lines with no
     * cost take it, flagged estimated. Only touches nulls, so it can't rewrite a real snapshot.
     * Flushes first so the product's own change is written in the same transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query("update SaleLine l set l.unitCost = :cost, l.costEstimated = true "
            + "where l.product = :product and l.unitCost is null")
    int fillMissingCost(@Param("product") Product product, @Param("cost") BigDecimal cost);

    // Sale has no persisted total (same "no cached derived column" call as
    // Product's balance) — the sales list needs it per row, so it's summed
    // from sale_lines on the fly rather than stored on Sale at confirm time.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l where l.sale.id = :saleId")
    BigDecimal sumLineTotalBySaleId(@Param("saleId") Long saleId);

    // Per payment method (I1): expected cash is CASH sales, expected MoMo is
    // MOMO sales. Dashboard's "expected cash" — lineTotal is already net of discount
    // (SaleService.resolveLine), so this sum doubles as expected cash with
    // no separate calculation. Voided sales are excluded: Void's own
    // contract is "expected cash reduced," so a voided line must not count
    // here even though the row itself stays visible everywhere else.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.voided = false "
            + "and l.sale.paymentMethod = :method")
    BigDecimal sumLineTotalSince(@Param("shop") Shop shop, @Param("from") Instant from,
                                 @Param("method") PaymentMethod method);

    // Dashboard's "discounts today." Same voided exclusion as sumLineTotalSince.
    @Query("select coalesce(sum(l.discount), 0) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.voided = false")
    BigDecimal sumDiscountSince(@Param("shop") Shop shop, @Param("from") Instant from);

    // Day close's totals: a closed range instead of "since now," so a picked
    // day's totals don't include everything after it. Same voided exclusion.
    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to and l.sale.voided = false "
            + "and l.sale.paymentMethod = :method")
    BigDecimal sumLineTotalBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to,
                                   @Param("method") PaymentMethod method);

    @Query("select coalesce(sum(l.discount), 0) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to and l.sale.voided = false")
    BigDecimal sumDiscountBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to);

    // Day close's per-sale discount column — SaleSummary deliberately doesn't
    // carry this (see Dashboard's decision doc: widening it would touch two
    // consumers that don't need it), so Day close sums it itself per sale,
    // same shape as sumLineTotalBySaleId.
    @Query("select coalesce(sum(l.discount), 0) from SaleLine l where l.sale.id = :saleId")
    BigDecimal sumDiscountBySaleId(@Param("saleId") Long saleId);

    // ---- I4 reports. All scoped by shop, range [from, to), voided sales excluded. ----
    // "Costed" = the line has a unit_cost (a real snapshot or one filled by the one-time fill).
    // Profit is only worked out over costed lines, so a line with no cost never looks like pure profit.

    @Query("select coalesce(sum(l.lineTotal), 0) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to "
            + "and l.sale.voided = false and l.unitCost is not null")
    BigDecimal sumCostedRevenueBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(l.quantity * l.unitCost), 0) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to "
            + "and l.sale.voided = false and l.unitCost is not null")
    BigDecimal sumCostOfGoodsBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(l) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to "
            + "and l.sale.voided = false")
    long countLinesBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(l) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to "
            + "and l.sale.voided = false and l.unitCost is not null")
    long countCostedLinesBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(l) from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to "
            + "and l.sale.voided = false and l.unitCost is not null and l.costEstimated = true")
    long countEstimatedLinesBetween(@Param("shop") Shop shop, @Param("from") Instant from, @Param("to") Instant to);

    /** One row per product: {productId, name, revenue, cost, profit}, best profit first. Costed lines only. */
    @Query("select l.product.id, l.product.name, sum(l.lineTotal), sum(l.quantity * l.unitCost), "
            + "sum(l.lineTotal - l.quantity * l.unitCost) "
            + "from SaleLine l "
            + "where l.sale.shop = :shop and l.sale.createdAt >= :from and l.sale.createdAt < :to "
            + "and l.sale.voided = false and l.unitCost is not null "
            + "group by l.product.id, l.product.name "
            + "order by sum(l.lineTotal - l.quantity * l.unitCost) desc, l.product.name asc")
    List<Object[]> topProductsByProfit(@Param("shop") Shop shop, @Param("from") Instant from,
                                       @Param("to") Instant to, Pageable limit);
}
