package org.example.backend.sale;

import org.example.backend.shop.Shop;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.example.backend.user.User;

import java.time.Instant;

@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Per-shop number from ShopRepository.nextReceiptNumber; unique together
    // with shop_id, not on its own.
    @Column(name = "receipt_number", nullable = false, updatable = false)
    private Long receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(nullable = false)
    private boolean voided = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private Shop shop;

    protected Sale() {
    }

    public Sale(User staff, Long receiptNumber) {
        this.staff = staff;
        this.shop = staff.getShop();
        this.receiptNumber = receiptNumber;
    }

    public Shop getShop() {
        return shop;
    }

    public Long getId() {
        return id;
    }

    public Long getReceiptNumber() {
        return receiptNumber;
    }

    public User getStaff() {
        return staff;
    }

    public boolean isVoided() {
        return voided;
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
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
