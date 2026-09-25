package org.example.backend.sale;

import org.example.backend.product.Product;
import org.example.backend.staff.StaffPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    public List<SaleableProductResponse> products() {
        return saleService.listSaleableProducts().stream().map(SaleableProductResponse::from).toList();
    }

    @PostMapping
    public SaleResponse confirm(@RequestBody ConfirmSaleRequest request,
                                 @AuthenticationPrincipal StaffPrincipal staff) {
        List<SaleService.LineRequest> lines = request.lines() == null ? List.of() : request.lines().stream()
                .map(line -> new SaleService.LineRequest(line.productId(), line.quantity(), line.discount()))
                .toList();
        return SaleResponse.from(saleService.confirmSale(lines, staff.userId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record SaleableProductResponse(Long productId, String name, String unit, BigDecimal price) {
        static SaleableProductResponse from(Product product) {
            return new SaleableProductResponse(product.getId(), product.getName(), product.getUnit().name(),
                    product.getPrice());
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
