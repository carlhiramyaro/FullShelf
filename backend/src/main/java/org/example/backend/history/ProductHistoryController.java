package org.example.backend.history;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;

/**
 * Owner-only, under /api/owner/** so the Clerk-JWT chain already gates it. Read-only: corrections
 * still go through Reverse entry and Void. The shop comes from CurrentShop; the product id in
 * the URL is looked up inside it.
 */
@RestController
@RequestMapping("/api/owner/products/{id}/history")
public class ProductHistoryController {

    private final ProductHistoryService productHistoryService;

    public ProductHistoryController(ProductHistoryService productHistoryService) {
        this.productHistoryService = productHistoryService;
    }

    @GetMapping
    public ProductHistoryService.History history(@PathVariable Long id,
                                                 @RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(defaultValue = "0") int page) {
        return productHistoryService.history(id, from, to, type, page);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadInput(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadParam(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("Invalid " + ex.getName() + ": " + ex.getValue());
    }

    public record ErrorResponse(String message) {
    }
}
