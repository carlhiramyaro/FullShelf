package org.example.backend.dashboard;

import org.example.backend.stock.StockLevelController.StockLevelResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Owner-only landing screen (mvp.md's "Dashboard"). Under /api/owner/**, so
 * already gated by the Clerk-JWT chain. One GET returning everything the
 * screen needs in a single fetch, since it's one screen with several stats
 * rather than several independent widgets. Pure read, no mutation — no
 * @ExceptionHandler, same reasoning as StockLevelController.
 */
@RestController
@RequestMapping("/api/owner/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse summary() {
        DashboardService.DashboardSummary summary = dashboardService.summary();
        return new DashboardResponse(
                summary.totals().grossSales(),
                summary.totals().discounts(),
                summary.totals().expectedCash(),
                summary.lowStock().stream().map(StockLevelResponse::from).toList(),
                summary.negativeStock().stream().map(StockLevelResponse::from).toList());
    }

    public record DashboardResponse(BigDecimal salesToday, BigDecimal discountsToday, BigDecimal expectedCash,
                                     List<StockLevelResponse> lowStock, List<StockLevelResponse> negativeStock) {
    }
}
