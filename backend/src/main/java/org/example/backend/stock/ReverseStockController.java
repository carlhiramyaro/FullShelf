package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Owner-only reversal of a mistyped opening or received line. Sits under
 * /api/owner/** so it's already gated by the existing Clerk-JWT chain from
 * Phase A. Two GET endpoints because reversing needs picking a specific
 * ledger row, not just a product: a product list with a reversible-entry
 * count, then that product's actual reversible rows.
 */
@RestController
@RequestMapping("/api/owner/stock/reverse")
public class ReverseStockController {

    private final ReverseStockService reverseStockService;
    private final UserRepository userRepository;

    public ReverseStockController(ReverseStockService reverseStockService, UserRepository userRepository) {
        this.reverseStockService = reverseStockService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ProductReverseResponse> list() {
        return reverseStockService.listStatus().stream().map(ProductReverseResponse::from).toList();
    }

    @GetMapping("/{productId}")
    public List<ReversibleMovementResponse> movements(@PathVariable Long productId) {
        return reverseStockService.reversibleMovements(productId).stream()
                .map(ReversibleMovementResponse::from)
                .toList();
    }

    @PostMapping
    public ReverseResponse reverseEntry(@RequestBody ReverseStockRequest request, @AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER user.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        StockMovement reversal = reverseStockService.reverseEntry(request.movementId(), request.note(), owner);
        return ReverseResponse.from(reversal);
    }

    // IllegalStateException is included here (unlike the sibling stock
    // controllers) because StockMovementService.reverse — not a DB
    // constraint — is the authoritative guard against reversing the same
    // row twice, and it raises IllegalStateException for that case.
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex instanceof DataIntegrityViolationException
                ? "That entry could not be reversed"
                : ex.getMessage());
    }

    public record ReverseStockRequest(Long movementId, String note) {
    }

    public record ProductReverseResponse(Long productId, String name, String unit, int reversibleCount,
                                          BigDecimal currentBalance) {
        static ProductReverseResponse from(ReverseStockService.ProductReverseStatus status) {
            Product product = status.product();
            return new ProductReverseResponse(product.getId(), product.getName(), product.getUnit().name(),
                    status.reversibleCount(), status.currentBalance());
        }
    }

    public record ReversibleMovementResponse(Long movementId, String type, BigDecimal quantity, String note,
                                               Instant createdAt) {
        static ReversibleMovementResponse from(StockMovement movement) {
            return new ReversibleMovementResponse(movement.getId(), movement.getType().name(),
                    movement.getQuantity(), movement.getNote(), movement.getCreatedAt());
        }
    }

    public record ReverseResponse(Long movementId, Long originalMovementId, String type, BigDecimal quantity) {
        static ReverseResponse from(StockMovement reversal) {
            return new ReverseResponse(reversal.getId(), reversal.getReversedMovement().getId(),
                    reversal.getType().name(), reversal.getQuantity());
        }
    }

    public record ErrorResponse(String message) {
    }
}
