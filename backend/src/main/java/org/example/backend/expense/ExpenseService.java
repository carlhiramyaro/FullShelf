package org.example.backend.expense;

import org.example.backend.shop.CurrentShop;
import org.example.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Owner-only expenses (finance-scope.md section 3). Adds, lists by date range and
 * voids; there is no edit. Results are flat DTOs built inside the transaction,
 * since createdBy/voidedBy are lazy and open-in-view is off.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CurrentShop currentShop;

    public ExpenseService(ExpenseRepository expenseRepository, CurrentShop currentShop) {
        this.expenseRepository = expenseRepository;
        this.currentShop = currentShop;
    }

    @Transactional
    public ExpenseView add(LocalDate date, BigDecimal amount, String category, String paidFrom, String note,
                           User owner) {
        ExpenseCategory parsedCategory = ExpenseCategory.parse(category);
        ExpensePaidFrom parsedPaidFrom = ExpensePaidFrom.parse(paidFrom);
        LocalDate expenseDate = date == null ? today() : date;
        if (expenseDate.isAfter(today())) {
            throw new IllegalArgumentException("The date can't be in the future");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be more than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimal places");
        }
        String cleanNote = blankToNull(note);
        // "Other" would otherwise mean "unexplained".
        if (parsedCategory == ExpenseCategory.OTHER && cleanNote == null) {
            throw new IllegalArgumentException("Add a note when the category is Other");
        }
        Expense expense = expenseRepository.save(new Expense(owner, expenseDate, amount.setScale(2),
                parsedCategory, parsedPaidFrom, cleanNote));
        return ExpenseView.from(expense);
    }

    @Transactional
    public ExpenseView voidExpense(Long id, String note, User owner) {
        Expense expense = expenseRepository.findByIdAndShop(id, currentShop.get())
                .orElseThrow(() -> new IllegalArgumentException("No expense " + id));
        if (expense.isVoided()) {
            throw new IllegalStateException("Expense " + id + " has already been voided");
        }
        expense.voidBy(owner, blankToNull(note));
        return ExpenseView.from(expenseRepository.save(expense));
    }

    /** Month to date when no range is given. Voided rows are listed but not totalled. */
    @Transactional(readOnly = true)
    public ExpenseList list(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? today() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("The start date is after the end date");
        }
        List<ExpenseView> rows = expenseRepository
                .findByShopAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(currentShop.get(), start, end)
                .stream().map(ExpenseView::from).toList();

        BigDecimal total = BigDecimal.ZERO;
        Map<ExpensePaidFrom, BigDecimal> byPaidFrom = new EnumMap<>(ExpensePaidFrom.class);
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseView row : rows) {
            if (row.voided()) {
                continue;
            }
            total = total.add(row.amount());
            byPaidFrom.merge(row.paidFrom(), row.amount(), BigDecimal::add);
            byCategory.merge(row.category(), row.amount(), BigDecimal::add);
        }
        List<CategoryTotal> categories = new ArrayList<>();
        byCategory.forEach((category, amount) -> categories.add(new CategoryTotal(category, category.label(), amount)));
        return new ExpenseList(start, end, rows, total,
                byPaidFrom.getOrDefault(ExpensePaidFrom.CASH, BigDecimal.ZERO),
                byPaidFrom.getOrDefault(ExpensePaidFrom.MOMO, BigDecimal.ZERO),
                byPaidFrom.getOrDefault(ExpensePaidFrom.OTHER, BigDecimal.ZERO),
                categories);
    }

    // Same UTC "today" as everywhere (Ghana only).
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public record ExpenseView(Long id, LocalDate expenseDate, BigDecimal amount, ExpenseCategory category,
                              String categoryLabel, ExpensePaidFrom paidFrom, String note, String createdByName,
                              Instant createdAt, boolean voided, Instant voidedAt, String voidNote) {
        static ExpenseView from(Expense e) {
            return new ExpenseView(e.getId(), e.getExpenseDate(), e.getAmount(), e.getCategory(),
                    e.getCategory().label(), e.getPaidFrom(), e.getNote(), e.getCreatedBy().getName(),
                    e.getCreatedAt(), e.isVoided(), e.getVoidedAt(), e.getVoidNote());
        }
    }

    public record CategoryTotal(ExpenseCategory category, String label, BigDecimal amount) {
    }

    public record ExpenseList(LocalDate from, LocalDate to, List<ExpenseView> expenses, BigDecimal total,
                              BigDecimal paidFromCash, BigDecimal paidFromMomo, BigDecimal paidFromOther,
                              List<CategoryTotal> byCategory) {
    }
}
