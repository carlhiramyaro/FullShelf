package org.example.backend.sale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner's side of the same read model SaleController exposes to staff, in
 * its own controller rather than one shared one — /api/owner/** and
 * /api/staff/** are separate SecurityFilterChains (Clerk JWT vs. PIN-session
 * token), so no single path can sit in both, and unlike every other
 * role-split in this app so far, this is the first query both roles need.
 * Both controllers delegate to the same SaleQueryService; only the
 * "today"-only restriction (enforced in SaleController) differs.
 */
@RestController
@RequestMapping("/api/owner/sales")
public class OwnerSaleController {

    private final SaleQueryService saleQueryService;

    public OwnerSaleController(SaleQueryService saleQueryService) {
        this.saleQueryService = saleQueryService;
    }

    @GetMapping
    public List<SaleQueryService.SaleSummary> list() {
        return saleQueryService.listAll();
    }

    @GetMapping("/{receiptNumber}")
    public SaleQueryService.SaleDetail receipt(@PathVariable Long receiptNumber) {
        return saleQueryService.findByReceiptNumber(receiptNumber);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
