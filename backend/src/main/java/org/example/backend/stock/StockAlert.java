package org.example.backend.stock;

import org.example.backend.product.Product;
import org.example.backend.shop.Shop;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Whether a low/negative-stock alert is currently "open" for a product — set
 * true the first time a movement crosses it below the alert level, cleared
 * only when the product is received again. See StockCrossingListener for the
 * crossing/reset rules.
 */
@Entity
@Table(name = "stock_alerts")
public class StockAlert {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private Shop shop;

    protected StockAlert() {
    }

    public StockAlert(Product product) {
        this.productId = product.getId();
        this.shop = product.getShop();
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public Long getProductId() {
        return productId;
    }

    public boolean isActive() {
        return active;
    }

    public void trigger() {
        this.active = true;
        this.triggeredAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void reset() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
