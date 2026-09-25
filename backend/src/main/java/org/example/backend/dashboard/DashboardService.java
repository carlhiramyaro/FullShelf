package org.example.backend.dashboard;

import org.example.backend.sale.SaleQueryService;
import org.example.backend.stock.StockLevelService;
import org.example.backend.stock.StockLevelStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Composes the two read services that already exist for this data —
 * SaleQueryService (today's totals) and StockLevelService (the
 * NEGATIVE/LOW/OK split) — into one summary for mvp.md's Dashboard screen,
 * rather than re-deriving either figure a second way. StockLevelService's
 * own decision doc named Dashboard as its next caller for exactly this
 * split.
 */
@Service
public class DashboardService {

    private final SaleQueryService saleQueryService;
    private final StockLevelService stockLevelService;

    public DashboardService(SaleQueryService saleQueryService, StockLevelService stockLevelService) {
        this.saleQueryService = saleQueryService;
        this.stockLevelService = stockLevelService;
    }

    public DashboardSummary summary() {
        SaleQueryService.SaleTotals totals = saleQueryService.todayTotals();
        List<StockLevelService.ProductStockLevel> levels = stockLevelService.listLevels();
        List<StockLevelService.ProductStockLevel> lowStock =
                levels.stream().filter(level -> level.status() == StockLevelStatus.LOW).toList();
        List<StockLevelService.ProductStockLevel> negativeStock =
                levels.stream().filter(level -> level.status() == StockLevelStatus.NEGATIVE).toList();
        return new DashboardSummary(totals, lowStock, negativeStock);
    }

    public record DashboardSummary(SaleQueryService.SaleTotals totals,
                                    List<StockLevelService.ProductStockLevel> lowStock,
                                    List<StockLevelService.ProductStockLevel> negativeStock) {
    }
}
