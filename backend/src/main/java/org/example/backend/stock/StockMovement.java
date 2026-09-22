package org.example.backend.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.example.backend.product.Product;
import org.example.backend.sale.SaleLine;
import org.example.backend.user.User;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only ledger row. Quantity is signed (positive for opening/received,
 * negative for sale/write-off), so a product's balance is always
 * sum(quantity) over its rows — see StockBalanceService.
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementType type;

    // Set only on VOID/REVERSAL rows: the movement this one negates. The
    // original row is never edited or deleted, so it stays visible in the log.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_movement_id")
    private StockMovement reversedMovement;

    // Set only on SALE-derived rows, so Void can find the exact movement to
    // reverse for each line of a sale.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_line_id")
    private SaleLine saleLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(Product product, BigDecimal quantity, StockMovementType type, User performedBy) {
        this.product = product;
        this.quantity = quantity;
        this.type = type;
        this.performedBy = performedBy;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public StockMovementType getType() {
        return type;
    }

    public StockMovement getReversedMovement() {
        return reversedMovement;
    }

    public void setReversedMovement(StockMovement reversedMovement) {
        this.reversedMovement = reversedMovement;
    }

    public SaleLine getSaleLine() {
        return saleLine;
    }

    public void setSaleLine(SaleLine saleLine) {
        this.saleLine = saleLine;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
