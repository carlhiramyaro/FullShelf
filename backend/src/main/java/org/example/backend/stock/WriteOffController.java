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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner-only write-off entry. Sits under /api/owner/** so it's already
 * gated by the existing Clerk-JWT chain from Phase A. One product per
 * request, matching Receive Stock's shape — a write-off is one
 * spoilage/damage incident, not a multi-product form.
 */
@RestController
@RequestMapping("/api/owner/stock/write-off")
public class WriteOffController {

    private final WriteOffService writeOffService;
    private final UserRepository userRepository;

    public WriteOffController(WriteOffService writeOffService, UserRepository userRepository) {
        this.writeOffService = writeOffService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ProductWriteOffResponse> list() {
        return writeOffService.listStatus().stream().map(ProductWriteOffResponse::from).toList();
    }

    @PostMapping
    public WriteOffResponse writeOff(@RequestBody WriteOffRequest request, @AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER user.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        StockMovement movement = writeOffService.writeOff(request.productId(), request.quantity(), request.note(), owner);
        return WriteOffResponse.from(movement);
    }

    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "That write-off could not be saved");
    }

    public record WriteOffRequest(Long productId, BigDecimal quantity, String note) {
    }

    public record ProductWriteOffResponse(Long productId, String name, String unit, BigDecimal currentBalance) {
        static ProductWriteOffResponse from(WriteOffService.ProductWriteOffStatus status) {
            Product product = status.product();
            return new ProductWriteOffResponse(product.getId(), product.getName(), product.getUnit().name(),
                    status.currentBalance());
        }
    }

    public record WriteOffResponse(Long movementId, BigDecimal quantity, String note) {
        static WriteOffResponse from(StockMovement movement) {
            return new WriteOffResponse(movement.getId(), movement.getQuantity().negate(), movement.getNote());
        }
    }

    public record ErrorResponse(String message) {
    }
}
