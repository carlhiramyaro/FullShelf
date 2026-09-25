package org.example.backend.sale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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

    public record SaleLineDetail(Long productId, String productName, String unit, BigDecimal quantity,
                                  BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {
        static SaleLineDetail from(SaleLine line) {
            return new SaleLineDetail(line.getProduct().getId(), line.getProduct().getName(),
                    line.getProduct().getUnit().name(), line.getQuantity(), line.getUnitPrice(), line.getDiscount(),
                    line.getLineTotal());
        }
    }
}
