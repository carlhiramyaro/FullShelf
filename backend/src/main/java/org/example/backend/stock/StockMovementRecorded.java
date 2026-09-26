package org.example.backend.stock;

import org.example.backend.product.Product;

/**
 * Published by StockMovementService after every ledger write, so
 * crossing-detection (StockCrossingListener) doesn't need a direct dependency
 * edge from the shared ledger-write primitive — and so every current and
 * future caller of record()/reverse() gets crossing detection for free,
 * without needing to remember a second call.
 */
public record StockMovementRecorded(Product product, StockMovementType type) {
}
