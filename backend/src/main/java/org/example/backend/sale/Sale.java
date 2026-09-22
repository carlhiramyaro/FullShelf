package org.example.backend.sale;

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

    // Assigned by the DB sequence default (sale_receipt_number_seq); not set on
    // insert from the Java side, so re-read the entity after saving to see it.
    @Column(name = "receipt_number", insertable = false, updatable = false)
    private Long receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(nullable = false)
    private boolean voided = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Sale() {
    }

    public Sale(User staff) {
        this.staff = staff;
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
