package org.example.backend.expense;

import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.product.ProductUnit;
import org.example.backend.sale.PaymentMethod;
import org.example.backend.sale.SaleQueryService;
import org.example.backend.sale.SaleService;
import org.example.backend.shop.CurrentShop;
import org.example.backend.shop.Shop;
import org.example.backend.shop.ShopRepository;
import org.example.backend.user.User;
import org.example.backend.user.UserRepository;
import org.example.backend.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * I3: expenses and what they do to expected cash / expected MoMo. Uses fresh shops so
 * the dev database's rows in shop #1 can't move the totals.
 */
@SpringBootTest
@Transactional
class ExpenseIntegrationTest {

    @Autowired private CurrentShop currentShop;
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ExpenseService expenseService;
    @Autowired private SaleService saleService;
    @Autowired private SaleQueryService saleQueryService;

    private Shop shop;
    private User owner;
    private User staff;
    private Product product;

    @BeforeEach
    void setUp() {
        shop = shopRepository.saveAndFlush(new Shop("Expense Shop A"));
        owner = userRepository.save(new User(shop, "Expense Owner A", UserRole.OWNER));
        staff = new User(shop, "Expense Staff A", UserRole.STAFF);
        staff.setPinHash("x");
        staff = userRepository.save(staff);
        product = productRepository.save(
                new Product(shop, "Expense Oil", ProductUnit.UNIT, new BigDecimal("10.00"), new BigDecimal("2.00")));
        currentShop.set(shop);
    }

    @AfterEach
    void clear() {
        currentShop.clear();
    }

    private LocalDate today() {
        return ExpenseService.today();
    }

    private ExpenseService.ExpenseView add(LocalDate date, String amount, String category, String paidFrom, String note) {
        return expenseService.add(date, new BigDecimal(amount), category, paidFrom, note, owner);
    }

    private void sell(int units, PaymentMethod method) {
        saleService.confirmSale(
                List.of(new SaleService.LineRequest(product.getId(), new BigDecimal(units), BigDecimal.ZERO)),
                staff.getId(), method);
    }

    @Test
    void cashAndMomoExpensesReduceTheirOwnExpectedFigureAndOtherReducesNeither() {
        sell(10, PaymentMethod.CASH); // 100.00
        sell(5, PaymentMethod.MOMO);  // 50.00
        add(today(), "30.00", "RENT", "CASH", null);
        add(today(), "20.00", "TRANSPORT", "MOMO", null);
        add(today(), "999.00", "SUPPLIES", "OTHER", null);

        SaleQueryService.SaleTotals totals = saleQueryService.todayTotals();
        assertThat(totals.expectedCash()).isEqualByComparingTo("70.00");
        assertThat(totals.expectedMomo()).isEqualByComparingTo("30.00");
        assertThat(totals.salesCash()).isEqualByComparingTo("100.00");
        assertThat(totals.expensesCash()).isEqualByComparingTo("30.00");
        assertThat(totals.expensesMomo()).isEqualByComparingTo("20.00");
        // Sales themselves are untouched by expenses.
        assertThat(totals.grossSales()).isEqualByComparingTo("150.00");
    }

    @Test
    void aBackdatedExpenseLandsOnItsOwnDayNotOnToday() {
        LocalDate yesterday = today().minusDays(1);
        add(yesterday, "40.00", "RENT", "CASH", null);

        assertThat(saleQueryService.dayTotals(yesterday).expectedCash()).isEqualByComparingTo("-40.00");
        assertThat(saleQueryService.todayTotals().expectedCash()).isEqualByComparingTo("0");
    }

    @Test
    void aVoidedExpenseStopsCountingButStaysInTheList() {
        ExpenseService.ExpenseView expense = add(today(), "25.00", "PACKAGING", "CASH", null);
        assertThat(saleQueryService.todayTotals().expectedCash()).isEqualByComparingTo("-25.00");

        ExpenseService.ExpenseView voided = expenseService.voidExpense(expense.id(), " mistyped ", owner);
        assertThat(voided.voided()).isTrue();
        assertThat(voided.voidNote()).isEqualTo("mistyped");
        assertThat(saleQueryService.todayTotals().expectedCash()).isEqualByComparingTo("0");

        ExpenseService.ExpenseList list = expenseService.list(today(), today());
        assertThat(list.expenses()).hasSize(1);
        assertThat(list.expenses().get(0).voided()).isTrue();
        assertThat(list.total()).isEqualByComparingTo("0");
        assertThat(list.byCategory()).isEmpty();
    }

    @Test
    void anExpenseCanOnlyBeVoidedOnce() {
        ExpenseService.ExpenseView expense = add(today(), "5.00", "SUPPLIES", "CASH", null);
        expenseService.voidExpense(expense.id(), null, owner);
        assertThatThrownBy(() -> expenseService.voidExpense(expense.id(), null, owner))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listTotalsSplitByPaidFromAndCategoryAndRespectTheRange() {
        add(today(), "10.00", "RENT", "CASH", null);
        add(today(), "15.50", "RENT", "MOMO", null);
        add(today(), "4.50", "SALARIES", "OTHER", null);
        add(today().minusDays(10), "100.00", "RENT", "CASH", null);

        ExpenseService.ExpenseList list = expenseService.list(today().minusDays(1), today());
        assertThat(list.expenses()).hasSize(3);
        assertThat(list.total()).isEqualByComparingTo("30.00");
        assertThat(list.paidFromCash()).isEqualByComparingTo("10.00");
        assertThat(list.paidFromMomo()).isEqualByComparingTo("15.50");
        assertThat(list.paidFromOther()).isEqualByComparingTo("4.50");
        assertThat(list.byCategory()).extracting(ExpenseService.CategoryTotal::category)
                .containsExactly(ExpenseCategory.RENT, ExpenseCategory.SALARIES);
        assertThat(list.byCategory().get(0).amount()).isEqualByComparingTo("25.50");
    }

    @Test
    void validationRejectsBadInputAndWritesNothing() {
        assertThatThrownBy(() -> add(today(), "0", "RENT", "CASH", null)).hasMessageContaining("more than zero");
        assertThatThrownBy(() -> add(today(), "-3", "RENT", "CASH", null)).hasMessageContaining("more than zero");
        assertThatThrownBy(() -> add(today(), "1.234", "RENT", "CASH", null)).hasMessageContaining("2 decimal");
        assertThatThrownBy(() -> add(today().plusDays(1), "5", "RENT", "CASH", null)).hasMessageContaining("future");
        assertThatThrownBy(() -> add(today(), "5", "FUN", "CASH", null)).hasMessageContaining("Unknown category");
        assertThatThrownBy(() -> add(today(), "5", "RENT", "BANK", null)).hasMessageContaining("Paid from");
        assertThatThrownBy(() -> add(today(), "5", "RENT", null, null)).hasMessageContaining("paid from");
        assertThatThrownBy(() -> add(today(), "5", "OTHER", "CASH", "  ")).hasMessageContaining("note");

        assertThat(expenseService.list(today().minusDays(30), today()).expenses()).isEmpty();
        add(today(), "5", "OTHER", "CASH", "gift for the landlord"); // a note makes Other fine
        add(null, "5", "RENT", "cash", null);                         // no date means today; case is forgiven
        assertThat(expenseService.list(today(), today()).expenses()).hasSize(2);
    }

    @Test
    void anotherShopsExpensesAndIdsAreInvisible() {
        ExpenseService.ExpenseView mine = add(today(), "50.00", "RENT", "CASH", null);

        Shop other = shopRepository.saveAndFlush(new Shop("Expense Shop B"));
        User otherOwner = userRepository.save(new User(other, "Expense Owner B", UserRole.OWNER));
        currentShop.set(other);

        assertThat(expenseService.list(today(), today()).expenses()).isEmpty();
        assertThat(saleQueryService.todayTotals().expectedCash()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> expenseService.voidExpense(mine.id(), null, otherOwner))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No expense");

        currentShop.set(shop);
        assertThat(expenseService.list(today(), today()).expenses()).extracting(ExpenseService.ExpenseView::voided)
                .containsExactly(false);
        assertThat(saleQueryService.todayTotals().expectedCash()).isEqualByComparingTo("-50.00");
    }
}
