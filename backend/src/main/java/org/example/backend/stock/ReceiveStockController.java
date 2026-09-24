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
 * Owner-only, per-delivery receive-stock entry. Sits under /api/owner/**
 * so it's already gated by the existing Clerk-JWT chain from Phase A — no
 * new security code needed here. One product per request, matching mvp.md's
 * "one product at a time" (unlike Opening Stock's whole-catalog batch).
 */
@RestController
@RequestMapping("/api/owner/stock/receive")
public class ReceiveStockController {

    private final ReceiveStockService receiveStockService;
    private final UserRepository userRepository;

    public ReceiveStockController(ReceiveStockService receiveStockService, UserRepository userRepository) {
        this.receiveStockService = receiveStockService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ProductReceiveResponse> list() {
        return receiveStockService.listStatus().stream().map(ProductReceiveResponse::from).toList();
    }

    @PostMapping
    public ReceiveResponse receive(@RequestBody ReceiveStockRequest request, @AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER user.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        ReceiveStockService.ReceiveResult result = receiveStockService.receive(
                request.productId(), request.cartonCount(), request.actualQuantity(), owner);
        return ReceiveResponse.from(result);
    }

    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "That delivery could not be saved");
    }

    public record ReceiveStockRequest(Long productId, Integer cartonCount, BigDecimal actualQuantity) {
    }

    public record ProductReceiveResponse(Long productId, String name, String unit, BigDecimal cartonWeight,
                                          BigDecimal currentBalance) {
        static ProductReceiveResponse from(ReceiveStockService.ProductReceiveStatus status) {
            Product product = status.product();
            return new ProductReceiveResponse(product.getId(), product.getName(), product.getUnit().name(),
                    product.getCartonWeight(), status.currentBalance());
        }
    }

    public record ReceiveResponse(Long movementId, BigDecimal actualQuantity, BigDecimal nominal, BigDecimal gap) {
        static ReceiveResponse from(ReceiveStockService.ReceiveResult result) {
            return new ReceiveResponse(result.movement().getId(), result.movement().getQuantity(),
                    result.nominal(), result.gap());
        }
    }

    public record ErrorResponse(String message) {
    }
}
