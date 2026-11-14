package org.example.backend.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.example.backend.product.Product;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_lines")
public class SaleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    // Snapshot of Product.price at sale time — Product.price can change later
    // without altering what a past receipt shows.
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    // Snapshot of Product.costPrice at sale time, like unitPrice. Null when the product
    // had no cost yet; the first-time fill may set it later and flags it estimated.
    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "cost_estimated", nullable = false)
    private boolean costEstimated = false;

    protected SaleLine() {
    }

    public SaleLine(Sale sale, Product product, BigDecimal quantity, BigDecimal unitPrice,
                     BigDecimal discount, BigDecimal lineTotal, BigDecimal unitCost) {
        this.sale = sale;
        this.unitCost = unitCost;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discount = discount;
        this.lineTotal = lineTotal;
    }

    public Long getId() {
        return id;
    }

    public Sale getSale() {
        return sale;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public boolean isCostEstimated() {
        return costEstimated;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
