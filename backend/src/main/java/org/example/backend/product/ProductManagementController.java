package org.example.backend.product;

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
import java.util.List;

/**
 * Owner-only product catalog. Sits under /api/owner/** so it's already
 * gated by the existing Clerk-JWT chain from Phase A — no new security code
 * needed here.
 */
@RestController
@RequestMapping("/api/owner/products")
public class ProductManagementController {

    private final ProductManagementService productManagementService;

    private final UserRepository userRepository;

    public ProductManagementController(ProductManagementService productManagementService,
                                       UserRepository userRepository) {
        this.productManagementService = productManagementService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productManagementService.listProducts().stream().map(ProductResponse::from).toList();
    }

    @PostMapping
    public ProductResponse create(@RequestBody ProductRequest request, @AuthenticationPrincipal Jwt jwt) {
        Product product = productManagementService.createProduct(
                request.name(), request.unit(), request.price(), request.alertLevel(), request.cartonWeight(),
                request.costPrice(), owner(jwt));
        return ProductResponse.from(product);
    }

    @PostMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @RequestBody ProductRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        Product product = productManagementService.updateProduct(
                id, request.name(), request.unit(), request.price(), request.alertLevel(), request.cartonWeight(),
                request.costPrice(), owner(jwt));
        return ProductResponse.from(product);
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        productManagementService.deactivate(id);
    }

    // SecurityConfig already guarantees this JWT resolved to an OWNER user.
    private User owner(Jwt jwt) {
        return userRepository.findByClerkUserId(jwt.getSubject()).orElseThrow();
    }

    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "A product with that name already exists");
    }

    public record ProductRequest(String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                  BigDecimal cartonWeight, BigDecimal costPrice) {
    }

    public record ProductResponse(Long id, String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                   BigDecimal cartonWeight, boolean active, BigDecimal costPrice) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getUnit(), product.getPrice(),
                    product.getAlertLevel(), product.getCartonWeight(), product.isActive(),
                    product.getCostPrice());
        }
    }

    public record ErrorResponse(String message) {
    }
}
