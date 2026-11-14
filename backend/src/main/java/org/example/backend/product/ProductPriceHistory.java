package org.example.backend.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.example.backend.shop.Shop;
import org.example.backend.user.User;

import java.math.BigDecimal;
import java.time.Instant;

/** One row per change to a product's price or cost. Append-only: nothing here is ever edited. */
@Entity
@Table(name = "product_price_history")
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    // Copied from the product, like StockMovement, so a caller can't pass a different shop.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private Shop shop;

    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "cost_price", updatable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", updatable = false)
    private User changedBy;

    protected ProductPriceHistory() {
    }

    public ProductPriceHistory(Product product, User changedBy) {
        this.product = product;
        this.shop = product.getShop();
        this.price = product.getPrice();
        this.costPrice = product.getCostPrice();
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
