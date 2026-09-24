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
import java.util.stream.Collectors;

/**
 * Owner-only, one-time (per product) opening stock entry at go-live. Sits
 * under /api/owner/** so it's already gated by the existing Clerk-JWT chain
 * from Phase A — no new security code needed here.
 */
@RestController
@RequestMapping("/api/owner/stock/opening")
public class OpeningStockController {

    private final OpeningStockService openingStockService;
    private final UserRepository userRepository;

    public OpeningStockController(OpeningStockService openingStockService, UserRepository userRepository) {
        this.openingStockService = openingStockService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ProductOpeningResponse> list() {
        return openingStockService.listStatus().stream().map(ProductOpeningResponse::from).toList();
    }

    @PostMapping
    public void submit(@RequestBody OpeningStockRequest request, @AuthenticationPrincipal Jwt jwt) {
        // SecurityConfig already guarantees this JWT resolved to an OWNER user.
        User owner = userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
        openingStockService.recordOpeningBalances(
                request.entries().stream().collect(Collectors.toMap(Entry::productId, Entry::quantity)),
                owner);
    }

    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "That opening stock entry could not be saved");
    }

    public record Entry(Long productId, BigDecimal quantity) {
    }

    public record OpeningStockRequest(List<Entry> entries) {
    }

    public record ProductOpeningResponse(Long productId, String name, String unit, boolean alreadySet,
                                          BigDecimal currentBalance) {
        static ProductOpeningResponse from(OpeningStockService.ProductOpeningStatus status) {
            Product product = status.product();
            return new ProductOpeningResponse(product.getId(), product.getName(), product.getUnit().name(),
                    status.alreadySet(), status.currentBalance());
        }
    }

    public record ErrorResponse(String message) {
    }
}
