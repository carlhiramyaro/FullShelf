package org.example.backend.sale;

import org.example.backend.product.Product;
import org.example.backend.shop.CurrentShop;
import org.example.backend.staff.StaffPrincipal;
import org.example.backend.stock.StockBalanceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Staff-facing sales screen. Sits under /api/staff/** and isn't in
 * StaffAccessFilter's DEVICE_ONLY_PATHS, so both endpoints need an active
 * PIN session — matching mvp.md's "staff key sales only, and can't edit
 * anything."
 */
@RestController
@RequestMapping("/api/staff/sales")
public class SaleController {

    private final SaleService saleService;
    private final SaleQueryService saleQueryService;

    private final StockBalanceService stockBalanceService;
    private final CurrentShop currentShop;

    public SaleController(SaleService saleService, SaleQueryService saleQueryService,
                           StockBalanceService stockBalanceService, CurrentShop currentShop) {
        this.saleService = saleService;
        this.saleQueryService = saleQueryService;
        this.stockBalanceService = stockBalanceService;
        this.currentShop = currentShop;
    }

    @GetMapping
    public List<SaleableProductResponse> products() {
        // One grouped query for the whole shop, not one balance per tile.
        Map<Long, BigDecimal> balances = stockBalanceService.currentBalances(currentShop.get());
        return saleService.listSaleableProducts().stream()
                .map(p -> SaleableProductResponse.from(p, balances.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @PostMapping
    public SaleResponse confirm(@RequestBody ConfirmSaleRequest request,
                                 @AuthenticationPrincipal StaffPrincipal staff) {
        List<SaleService.LineRequest> lines = request.lines() == null ? List.of() : request.lines().stream()
                .map(line -> new SaleService.LineRequest(line.productId(), line.quantity(), line.discount()))
                .toList();
        return SaleResponse.from(saleService.confirmSale(lines, staff.userId()));
    }

    // "Staff see today's sales only" (mvp.md) — SaleQueryService.listToday()
    // already scopes this, so no extra filtering needed here.
    @GetMapping("/today")
    public List<SaleQueryService.SaleSummary> today() {
        return saleQueryService.listToday();
    }

    // Scoped to today the same way: a receipt from an earlier day 404s here
    // even though it exists, rather than letting a staff session browse
    // arbitrary past receipts by guessing numbers.
    @GetMapping("/{receiptNumber}")
    public SaleQueryService.SaleDetail receipt(@PathVariable Long receiptNumber) {
        SaleQueryService.SaleDetail detail = saleQueryService.findByReceiptNumber(receiptNumber);
        if (detail.createdAt().isBefore(saleQueryService.startOfToday())) {
            throw new IllegalArgumentException("No sale with receipt number " + receiptNumber);
        }
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record SaleableProductResponse(Long productId, String name, String unit, BigDecimal price,
                                          BigDecimal stock) {
        static SaleableProductResponse from(Product product, BigDecimal stock) {
            return new SaleableProductResponse(product.getId(), product.getName(), product.getUnit().name(),
                    product.getPrice(), stock);
        }
    }

    public record ConfirmSaleRequest(List<SaleLineRequest> lines) {
    }

    // No price field: the client shows an autofilled price for display, but
    // the server always re-fetches Product.price itself for the unitPrice
    // snapshot, so a tampered client request can never change what a sale
    // actually charges.
    public record SaleLineRequest(Long productId, BigDecimal quantity, BigDecimal discount) {
    }

    public record SaleResponse(Long receiptNumber, Instant createdAt, String staffName, List<LineResponse> lines,
                                BigDecimal total) {
        static SaleResponse from(SaleService.SaleResult result) {
            List<LineResponse> lines = result.lines().stream().map(LineResponse::from).toList();
            return new SaleResponse(result.receiptNumber(), result.sale().getCreatedAt(),
                    result.staff().getName(), lines, result.total());
        }
    }

    public record LineResponse(Long productId, String productName, String unit, BigDecimal quantity,
                                BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {
        static LineResponse from(SaleService.LineResult line) {
            return new LineResponse(line.product().getId(), line.product().getName(), line.product().getUnit().name(),
                    line.quantity(), line.unitPrice(), line.discount(), line.lineTotal());
        }
    }

    public record ErrorResponse(String message) {
    }
}
