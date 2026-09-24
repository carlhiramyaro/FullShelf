package org.example.backend.product;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

    public ProductManagementController(ProductManagementService productManagementService) {
        this.productManagementService = productManagementService;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productManagementService.listProducts().stream().map(ProductResponse::from).toList();
    }

    @PostMapping
    public ProductResponse create(@RequestBody ProductRequest request) {
        Product product = productManagementService.createProduct(
                request.name(), request.unit(), request.price(), request.alertLevel(), request.cartonWeight());
        return ProductResponse.from(product);
    }

    @PostMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @RequestBody ProductRequest request) {
        Product product = productManagementService.updateProduct(
                id, request.name(), request.unit(), request.price(), request.alertLevel(), request.cartonWeight());
        return ProductResponse.from(product);
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable Long id) {
        productManagementService.deactivate(id);
    }

    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(RuntimeException ex) {
        return new ErrorResponse(ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "A product with that name already exists");
    }

    public record ProductRequest(String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                  BigDecimal cartonWeight) {
    }

    public record ProductResponse(Long id, String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel,
                                   BigDecimal cartonWeight, boolean active) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getName(), product.getUnit(), product.getPrice(),
                    product.getAlertLevel(), product.getCartonWeight(), product.isActive());
        }
    }

    public record ErrorResponse(String message) {
    }
}
