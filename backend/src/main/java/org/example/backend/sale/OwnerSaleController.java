package org.example.backend.sale;

import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 *
 * Void also lives here rather than in its own controller: unlike the Phase C
 * stock slices (each a genuinely new resource/screen), Void is a mutating
 * action on the same Sale resource this controller already exposes via
 * GET /{receiptNumber}, with no screen of its own — just a button on the
 * existing receipt-detail page. VoidSaleService still stays a separate
 * service, since the reversal logic is real and worth isolating/testing.
 */
@RestController
@RequestMapping("/api/owner/sales")
public class OwnerSaleController {

    private final SaleQueryService saleQueryService;
    private final VoidSaleService voidSaleService;
    private final UserRepository userRepository;

    public OwnerSaleController(SaleQueryService saleQueryService, VoidSaleService voidSaleService,
                                UserRepository userRepository) {
        this.saleQueryService = saleQueryService;
        this.voidSaleService = voidSaleService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<SaleQueryService.SaleSummary> list() {
        return saleQueryService.listAll();
    }

    @GetMapping("/{receiptNumber}")
    public SaleQueryService.SaleDetail receipt(@PathVariable Long receiptNumber) {
        return saleQueryService.findByReceiptNumber(receiptNumber);
    }

    @PostMapping("/{receiptNumber}/void")
    public SaleQueryService.SaleDetail voidSale(@PathVariable Long receiptNumber,
                                                 @RequestBody(required = false) VoidSaleRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER user.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        String note = request == null ? null : request.note();
        voidSaleService.voidSale(receiptNumber, note, owner);
        return saleQueryService.findByReceiptNumber(receiptNumber);
    }

    // IllegalStateException (already voided) is included here, unlike the
    // plain IllegalArgumentException-only handler this controller had before
    // Void — same reasoning as ReverseStockController: the authoritative
    // "already reversed" guard lives in StockMovementService.reverse, which
    // raises IllegalStateException, not a DB constraint.
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record VoidSaleRequest(String note) {
    }

    public record ErrorResponse(String message) {
    }
}
