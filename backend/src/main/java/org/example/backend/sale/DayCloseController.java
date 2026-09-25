package org.example.backend.sale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * mvp.md's Day close: pick a date, see every sale for it plus totals. A new
 * controller (new resource, same one-controller-per-slice convention as
 * every other Phase C/D/E/F slice) but no new service — the query logic is
 * two new SaleQueryService methods (listForDay/dayTotals), not a separate
 * concern, for the same reason todayTotals() lives there rather than in a
 * dedicated service: one place owns "what a day's boundaries are" so Day
 * close's and Dashboard's definitions can't drift apart.
 *
 * One GET returning everything the screen needs, same "one endpoint per
 * screen" precedent Dashboard set, rather than a totals endpoint and a list
 * endpoint the frontend would have to join itself.
 *
 * Unlike Dashboard/Stock-per-system (pure GETs with no input, so no
 * @ExceptionHandler), this GET takes a caller-supplied date — an
 * unparseable one throws MethodArgumentTypeMismatchException, which needs a
 * 400 here rather than surfacing as an unhandled 500.
 */
@RestController
@RequestMapping("/api/owner/day-close")
public class DayCloseController {

    private final SaleQueryService saleQueryService;

    public DayCloseController(SaleQueryService saleQueryService) {
        this.saleQueryService = saleQueryService;
    }

    @GetMapping("/{date}")
    public DayCloseResponse dayClose(@PathVariable LocalDate date) {
        SaleQueryService.SaleTotals totals = saleQueryService.dayTotals(date);
        List<SaleQueryService.DaySaleSummary> sales = saleQueryService.listForDay(date);
        return new DayCloseResponse(totals.grossSales(), totals.discounts(), totals.expectedCash(), sales);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadDate(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("Invalid date: " + ex.getValue());
    }

    public record DayCloseResponse(BigDecimal totalSales, BigDecimal totalDiscounts, BigDecimal expectedCash,
                                    List<SaleQueryService.DaySaleSummary> sales) {
    }

    public record ErrorResponse(String message) {
    }
}
