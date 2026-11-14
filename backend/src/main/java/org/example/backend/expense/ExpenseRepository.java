package org.example.backend.expense;

import org.example.backend.shop.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndShop(Long id, Shop shop);

    // Both ends inclusive: expense_date is a calendar date, not an instant.
    List<Expense> findByShopAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(Shop shop, LocalDate from, LocalDate to);

    // What reduces expected cash / expected MoMo. Voided expenses never count.
    @Query("select coalesce(sum(e.amount), 0) from Expense e "
            + "where e.shop = :shop and e.expenseDate between :from and :to and e.voided = false "
            + "and e.paidFrom = :paidFrom")
    BigDecimal sumAmount(@Param("shop") Shop shop, @Param("from") LocalDate from, @Param("to") LocalDate to,
                         @Param("paidFrom") ExpensePaidFrom paidFrom);
}
