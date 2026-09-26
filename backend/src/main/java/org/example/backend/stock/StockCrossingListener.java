package org.example.backend.stock;

import org.example.backend.notify.Notifier;
import org.example.backend.product.Product;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Reacts to every StockMovementRecorded event to implement mvp.md's "one
 * message... once per crossing, resets after she receives new stock."
 *
 * A plain (synchronous) @EventListener, not @TransactionalEventListener's
 * AFTER_COMMIT phase: every integration test in this app wraps itself in
 * @Transactional specifically so nothing is ever committed (Phase A's
 * real-Postgres-no-Testcontainers convention), and an AFTER_COMMIT listener
 * would simply never fire under that rollback. Running synchronously, inside
 * the same transaction as the write it's reacting to, is fine here — the
 * notifier is a stub with nothing meaningful to fail on yet.
 */
@Component
public class StockCrossingListener {

    private final StockAlertRepository stockAlertRepository;
    private final StockBalanceService stockBalanceService;
    private final Notifier notifier;

    public StockCrossingListener(StockAlertRepository stockAlertRepository,
                                  StockBalanceService stockBalanceService,
                                  Notifier notifier) {
        this.stockAlertRepository = stockAlertRepository;
        this.stockBalanceService = stockBalanceService;
        this.notifier = notifier;
    }

    @EventListener
    public void onStockMovementRecorded(StockMovementRecorded event) {
        Product product = event.product();
        StockAlert alert = stockAlertRepository.findById(product.getId())
                .orElseGet(() -> new StockAlert(product.getId()));

        if (event.type() == StockMovementType.RECEIVED) {
            // Resets on receipt regardless of whether the new balance actually
            // clears the alert level — literal to mvp.md's wording, so a
            // top-up that doesn't clear the threshold still re-arms the alert.
            alert.reset();
            stockAlertRepository.save(alert);
            return;
        }

        if (alert.isActive()) {
            // Already alerted for this open crossing — nothing resets it
            // except a RECEIVED movement, so don't re-check the balance.
            return;
        }

        BigDecimal balance = stockBalanceService.currentBalance(product.getId());
        StockLevelStatus status = StockLevelStatus.forBalance(balance, product.getAlertLevel());
        if (status != StockLevelStatus.OK) {
            alert.trigger();
            stockAlertRepository.save(alert);
            notifier.send(product.getName() + " is at " + balance + " " + product.getUnit()
                    + " (alert level " + product.getAlertLevel() + ")");
        }
    }
}
