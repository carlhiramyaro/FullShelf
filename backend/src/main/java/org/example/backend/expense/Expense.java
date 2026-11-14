package org.example.backend.expense;

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
import org.example.backend.shop.Shop;
import org.example.backend.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Money the shop spent. Never edited: a wrong one is voided, and stays visible.
 * The shop is copied from the owner who created it, so no caller can pass a
 * different one (same as Sale taking it from its staff user).
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private Shop shop;

    @Column(name = "expense_date", nullable = false, updatable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "paid_from", nullable = false, updatable = false, length = 10)
    private ExpensePaidFrom paidFrom;

    @Column(updatable = false)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean voided = false;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by")
    private User voidedBy;

    @Column(name = "void_note")
    private String voidNote;

    protected Expense() {
    }

    public Expense(User createdBy, LocalDate expenseDate, BigDecimal amount, ExpenseCategory category,
                   ExpensePaidFrom paidFrom, String note) {
        this.createdBy = createdBy;
        this.shop = createdBy.getShop();
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.category = category;
        this.paidFrom = paidFrom;
        this.note = note;
    }

    public void voidBy(User owner, String note) {
        this.voided = true;
        this.voidedAt = Instant.now();
        this.voidedBy = owner;
        this.voidNote = note;
    }

    public Long getId() { return id; }
    public Shop getShop() { return shop; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public BigDecimal getAmount() { return amount; }
    public ExpenseCategory getCategory() { return category; }
    public ExpensePaidFrom getPaidFrom() { return paidFrom; }
    public String getNote() { return note; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isVoided() { return voided; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidNote() { return voidNote; }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
