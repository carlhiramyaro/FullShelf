package org.example.backend.stock;

import org.example.backend.product.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner-only "Stock per system" view. Sits under /api/owner/** so it's
 * already gated by the existing Clerk-JWT chain from Phase A. Pure read, no
 * mutation and no invalid-input path, so unlike its Phase C/E siblings this
 * controller has no @ExceptionHandler — there's nothing here that throws.
 */
@RestController
@RequestMapping("/api/owner/stock/levels")
public class StockLevelController {

    private final StockLevelService stockLevelService;

    public StockLevelController(StockLevelService stockLevelService) {
        this.stockLevelService = stockLevelService;
    }

    @GetMapping
    public List<StockLevelResponse> list() {
        return stockLevelService.listLevels().stream().map(StockLevelResponse::from).toList();
    }

    public record StockLevelResponse(Long productId, String name, String unit, BigDecimal currentBalance,
                                      BigDecimal alertLevel, StockLevelStatus status) {
        static StockLevelResponse from(StockLevelService.ProductStockLevel level) {
            Product product = level.product();
            return new StockLevelResponse(product.getId(), product.getName(), product.getUnit().name(),
                    level.currentBalance(), product.getAlertLevel(), level.status());
        }
    }
}
