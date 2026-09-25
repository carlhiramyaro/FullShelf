package org.example.backend.sale;

import org.example.backend.stock.StockMovement;
import org.example.backend.stock.StockMovementRepository;
import org.example.backend.stock.StockMovementService;
import org.example.backend.stock.StockMovementType;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owner-only cancellation of a wrongly keyed sale. Every SALE-type stock
 * movement carries a sale_line_id (built ahead in Phase A specifically for
 * this), so each line's movement can be found and reversed directly — no
 * need to touch StockBalanceService, since a VOID row reversing a SALE row
 * restores the balance on its own, same as any other reversal. Sale.voided
 * and every response DTO already carry `voided` (built ahead in the Phase D
 * sales-list slice), so nothing about *displaying* a voided sale needs to
 * change here — this slice only adds the write path.
 */
@Service
public class VoidSaleService {

    private final SaleRepository saleRepository;
    private final SaleLineRepository saleLineRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockMovementService stockMovementService;

    public VoidSaleService(SaleRepository saleRepository, SaleLineRepository saleLineRepository,
                            StockMovementRepository stockMovementRepository,
                            StockMovementService stockMovementService) {
        this.saleRepository = saleRepository;
        this.saleLineRepository = saleLineRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockMovementService = stockMovementService;
    }

    @Transactional
    public Sale voidSale(Long receiptNumber, String note, User owner) {
        Sale sale = saleRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new IllegalArgumentException("No sale with receipt number " + receiptNumber));
        if (sale.isVoided()) {
            throw new IllegalStateException("Sale " + receiptNumber + " has already been voided");
        }

        String voidNote = blankToNull(note);
        for (SaleLine line : saleLineRepository.findBySaleId(sale.getId())) {
            List<StockMovement> movements = stockMovementRepository.findBySaleLineId(line.getId());
            for (StockMovement movement : movements) {
                stockMovementService.reverse(movement, StockMovementType.VOID, owner, voidNote);
            }
        }

        sale.setVoided(true);
        return saleRepository.save(sale);
    }

    private String blankToNull(String note) {
        return (note == null || note.isBlank()) ? null : note.trim();
    }
}
