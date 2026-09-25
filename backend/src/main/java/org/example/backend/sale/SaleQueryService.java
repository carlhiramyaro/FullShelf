package org.example.backend.sale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Read side for Phase D's second slice — shared by both the staff sales-list
 * endpoint (today only) and the owner sales-list endpoint (all), since the
 * query logic itself doesn't differ by role, only which of listToday()/
 * listAll() a controller calls and whether it enforces the "today" cutoff
 * on a single receipt lookup. Everything is resolved to plain DTOs inside
 * the @Transactional methods, not returned as entities, since Sale.staff
 * and SaleLine.product are lazy associations that would otherwise throw
 * once the session closes at the controller boundary.
 */
@Service
public class SaleQueryService {

    private final SaleRepository saleRepository;
    private final SaleLineRepository saleLineRepository;

    public SaleQueryService(SaleRepository saleRepository, SaleLineRepository saleLineRepository) {
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
    }

    @Transactional(readOnly = true)
    public List<SaleSummary> listToday() {
        return summarize(saleRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(startOfToday()));
    }

    @Transactional(readOnly = true)
    public List<SaleSummary> listAll() {
        return summarize(saleRepository.findAllByOrderByCreatedAtDesc());
    }

    // Dashboard's sales/discounts/expected-cash figures. Gross sales isn't
    // stored anywhere (SaleLine only has the net lineTotal), so it's derived
    // as expectedCash + discounts rather than added as a new column — same
    // "no cached derived value" convention as everything else in this app.
    @Transactional(readOnly = true)
    public SaleTotals todayTotals() {
        BigDecimal expectedCash = saleLineRepository.sumLineTotalSince(startOfToday());
        BigDecimal discounts = saleLineRepository.sumDiscountSince(startOfToday());
        return new SaleTotals(expectedCash.add(discounts), discounts, expectedCash);
    }

    // Day close's list for a picked date. A separate DaySaleSummary rather
    // than SaleSummary, since Day close needs a per-sale discount figure and
    // SaleSummary deliberately doesn't carry one (Dashboard's decision doc:
    // widening it would touch the staff/owner sales-list consumers for a
    // field neither needs).
    @Transactional(readOnly = true)
    public List<DaySaleSummary> listForDay(LocalDate date) {
        Instant from = startOfDay(date);
        Instant to = startOfDay(date.plusDays(1));
        return saleRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(from, to)
                .stream()
                .map(sale -> new DaySaleSummary(sale.getReceiptNumber(), sale.getCreatedAt(),
                        sale.getStaff().getName(), saleLineRepository.sumLineTotalBySaleId(sale.getId()),
                        saleLineRepository.sumDiscountBySaleId(sale.getId()), sale.isVoided()))
                .toList();
    }

    // Day close's totals for a picked date — same derivation as
    // todayTotals(), just over a closed range instead of "since now."
    @Transactional(readOnly = true)
    public SaleTotals dayTotals(LocalDate date) {
        Instant from = startOfDay(date);
        Instant to = startOfDay(date.plusDays(1));
        BigDecimal expectedCash = saleLineRepository.sumLineTotalBetween(from, to);
        BigDecimal discounts = saleLineRepository.sumDiscountBetween(from, to);
        return new SaleTotals(expectedCash.add(discounts), discounts, expectedCash);
    }

    @Transactional(readOnly = true)
    public SaleDetail findByReceiptNumber(Long receiptNumber) {
        Sale sale = saleRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new IllegalArgumentException("No sale with receipt number " + receiptNumber));
        List<SaleLine> lines = saleLineRepository.findBySaleId(sale.getId());
        List<SaleLineDetail> lineDetails = lines.stream().map(SaleLineDetail::from).toList();
        BigDecimal total = lines.stream().map(SaleLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SaleDetail(sale.getReceiptNumber(), sale.getCreatedAt(), sale.getStaff().getName(),
                sale.isVoided(), total, lineDetails);
    }

    // Accra (this app's only shop, per mvp.md) is UTC+0 with no DST, so a
    // UTC calendar day and the shop's calendar day are the same day —
    // Instant.truncatedTo(DAYS) (which floors to the UTC epoch day) is
    // exactly "start of today" here with no ZoneId conversion needed. This
    // is a deliberate simplification tied to that one fact, not a general
    // "we don't care about time zones" stance.
    public Instant startOfToday() {
        return Instant.now().truncatedTo(ChronoUnit.DAYS);
    }

    // Same Accra-is-UTC simplification as startOfToday(), generalized to an
    // arbitrary picked date for Day close.
    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private List<SaleSummary> summarize(List<Sale> sales) {
        return sales.stream()
                .map(sale -> new SaleSummary(sale.getReceiptNumber(), sale.getCreatedAt(),
                        sale.getStaff().getName(), saleLineRepository.sumLineTotalBySaleId(sale.getId()),
                        sale.isVoided()))
                .toList();
    }

    public record SaleSummary(Long receiptNumber, Instant createdAt, String staffName, BigDecimal total,
                               boolean voided) {
    }

    public record SaleDetail(Long receiptNumber, Instant createdAt, String staffName, boolean voided,
                              BigDecimal total, List<SaleLineDetail> lines) {
    }

    public record SaleTotals(BigDecimal grossSales, BigDecimal discounts, BigDecimal expectedCash) {
    }

    public record DaySaleSummary(Long receiptNumber, Instant createdAt, String staffName, BigDecimal total,
                                  BigDecimal discount, boolean voided) {
    }

    public record SaleLineDetail(Long productId, String productName, String unit, BigDecimal quantity,
                                  BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {
        static SaleLineDetail from(SaleLine line) {
            return new SaleLineDetail(line.getProduct().getId(), line.getProduct().getName(),
                    line.getProduct().getUnit().name(), line.getQuantity(), line.getUnitPrice(), line.getDiscount(),
                    line.getLineTotal());
        }
    }
}
