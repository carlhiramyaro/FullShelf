package org.example.backend.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductUnit unit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "alert_level", nullable = false, precision = 10, scale = 2)
    private BigDecimal alertLevel;

    @Column(name = "carton_weight", precision = 10, scale = 2)
    private BigDecimal cartonWeight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() {
    }

    public Product(String name, ProductUnit unit, BigDecimal price, BigDecimal alertLevel) {
        this.name = name;
        this.unit = unit;
        this.price = price;
        this.alertLevel = alertLevel;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductUnit getUnit() {
        return unit;
    }

    public void setUnit(ProductUnit unit) {
        this.unit = unit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(BigDecimal alertLevel) {
        this.alertLevel = alertLevel;
    }

    public BigDecimal getCartonWeight() {
        return cartonWeight;
    }

    public void setCartonWeight(BigDecimal cartonWeight) {
        this.cartonWeight = cartonWeight;
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
