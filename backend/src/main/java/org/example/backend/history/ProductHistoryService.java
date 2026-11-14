package org.example.backend.history;

import jakarta.persistence.EntityManager;
import org.example.backend.product.Product;
import org.example.backend.product.ProductRepository;
import org.example.backend.shop.CurrentShop;
import org.example.backend.stock.StockBalanceService;
import org.example.backend.stock.StockMovementType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/**
 * Owner-only, read-only history of one product (finance-scope.md section 5). One timeline merges
 * the stock ledger with price/cost changes, so a single sort and a single page cover both.
 *
 * <p>The running balance is a window function over the product's WHOLE ledger, computed before
 * the date and type filters are applied: the balance after a row depends on every earlier row,
 * not only the ones on screen, so filtering first would show wrong balances.
 */
@Service
public class ProductHistoryService {

    public static final int PAGE_SIZE = 50;
    /** A pseudo-type for the type filter: price and cost changes, which are not ledger rows. */
    public static final String PRICE_CHANGE = "PRICE_CHANGE";

    // Ledger rows, with who did it, the receipt (a sale's own, or the one a void/reversal undoes)
    // and the balance after the row. Column order must match PRICES below for the UNION ALL.
    private static final String LEDGER = """
            select 'M' as kind, m.id as row_id, m.created_at as at, m.type as type, m.quantity as quantity,
                   sum(m.quantity) over (order by m.created_at, m.id) as balance_after,
                   u.name as who, m.note as note, m.reversed_movement_id as reversed_id,
                   coalesce(s1.receipt_number, s2.receipt_number) as receipt_number,
                   null::numeric as price, null::numeric as cost_price,
                   null::numeric as prev_price, null::numeric as prev_cost
            from stock_movements m
            join users u on u.id = m.performed_by
            left join sale_lines l1 on l1.id = m.sale_line_id
            left join sales s1 on s1.id = l1.sale_id
            left join stock_movements r on r.id = m.reversed_movement_id
            left join sale_lines l2 on l2.id = r.sale_line_id
            left join sales s2 on s2.id = l2.sale_id
            where m.product_id = :product and m.shop_id = :shop
            """;

    private static final String PRICES = """
            select 'P', h.id, h.changed_at, 'PRICE_CHANGE', null::numeric, null::numeric,
                   u.name, null, null::bigint, null::bigint,
                   h.price, h.cost_price,
                   lag(h.price) over (order by h.changed_at, h.id),
                   lag(h.cost_price) over (order by h.changed_at, h.id)
            from product_price_history h
            left join users u on u.id = h.changed_by
            where h.product_id = :product and h.shop_id = :shop
            """;

    private final ProductRepository productRepository;
    private final StockBalanceService stockBalanceService;
    private final CurrentShop currentShop;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;

    public ProductHistoryService(ProductRepository productRepository, StockBalanceService stockBalanceService,
                                 CurrentShop currentShop, NamedParameterJdbcTemplate jdbc,
                                 EntityManager entityManager) {
        this.productRepository = productRepository;
        this.stockBalanceService = stockBalanceService;
        this.currentShop = currentShop;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public History history(Long productId, LocalDate fromDate, LocalDate toDate, String type, int page) {
        // The product comes first and by shop, as in the Reverse entry leak fix: another shop's id is "No product N".
        Product product = productRepository.findByIdAndShop(productId, currentShop.get())
                .orElseThrow(() -> new IllegalArgumentException("No product " + productId));
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("The start date is after the end date");
        }
        String typeFilter = type == null || type.isBlank() ? null : type;
        if (typeFilter != null && !PRICE_CHANGE.equals(typeFilter) && Arrays.stream(StockMovementType.values())
                .noneMatch(t -> t.name().equals(typeFilter))) {
            throw new IllegalArgumentException("Unknown type " + typeFilter);
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page cannot be negative");
        }
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Plain JDBC doesn't see pending JPA changes on its own.
        entityManager.flush();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("product", product.getId())
                .addValue("shop", product.getShop().getId());
        // Conditions are added only when used: Postgres can't infer the type of an untyped NULL parameter.
        StringBuilder where = new StringBuilder(" where true");
        if (from != null) {
            where.append(" and t.at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            where.append(" and t.at < :to");
            params.addValue("to", Timestamp.from(to));
        }
        if (typeFilter != null) {
            where.append(" and t.type = :type");
            params.addValue("type", typeFilter);
        }
        String merged = "from (" + LEDGER + " union all " + PRICES + ") t" + where;

        Long total = jdbc.queryForObject("select count(*) " + merged, params, Long.class);
        params.addValue("limit", PAGE_SIZE).addValue("offset", (long) page * PAGE_SIZE);
        List<Row> rows = jdbc.query("select t.* " + merged
                        + " order by t.at desc, t.kind desc, t.row_id desc limit :limit offset :offset", params,
                (rs, i) -> new Row(
                        rs.getString("kind").equals("P") ? "PRICE_CHANGE" : "LEDGER",
                        rs.getLong("row_id"),
                        rs.getObject("at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getString("type"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("who"),
                        rs.getString("note"),
                        (Long) rs.getObject("receipt_number"),
                        (Long) rs.getObject("reversed_id"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("cost_price"),
                        rs.getBigDecimal("prev_price"),
                        rs.getBigDecimal("prev_cost")));

        BigDecimal balance = stockBalanceService.currentBalances(currentShop.get())
                .getOrDefault(product.getId(), BigDecimal.ZERO);
        return new History(
                new ProductInfo(product.getId(), product.getName(), product.getUnit().name(), product.getPrice(),
                        product.getCostPrice(), balance),
                fromDate, toDate, typeFilter, page, PAGE_SIZE, total, (long) (page + 1) * PAGE_SIZE < total,
                summary(product, from, to), rows);
    }

    private Summary summary(Product product, Instant from, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("product", product.getId())
                .addValue("shop", product.getShop().getId());
        String ledgerRange = "";
        String saleRange = "";
        if (from != null) {
            ledgerRange += " and m.created_at >= :from";
            saleRange += " and s.created_at >= :from";
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            ledgerRange += " and m.created_at < :to";
            saleRange += " and s.created_at < :to";
            params.addValue("to", Timestamp.from(to));
        }

        BigDecimal[] stock = new BigDecimal[3]; // received, written off, net change
        java.util.Arrays.fill(stock, BigDecimal.ZERO);
        jdbc.query("select m.type, coalesce(sum(m.quantity), 0) as qty from stock_movements m "
                + "where m.product_id = :product and m.shop_id = :shop" + ledgerRange + " group by m.type", params,
                rs -> {
                    BigDecimal qty = rs.getBigDecimal("qty");
                    String type = rs.getString("type");
                    stock[2] = stock[2].add(qty);
                    if (type.equals(StockMovementType.RECEIVED.name())) {
                        stock[0] = qty;
                    } else if (type.equals(StockMovementType.WRITE_OFF.name())) {
                        stock[1] = qty.negate();
                    }
                });

        // Sales figures use the same rule as Reports: voided sales don't count, and profit is
        // worked out over costed lines only (a line with no cost would look like pure profit).
        return jdbc.queryForObject("""
                select coalesce(sum(l.quantity), 0) as sold, coalesce(sum(l.line_total), 0) as revenue,
                       coalesce(sum(l.discount), 0) as discounts, count(distinct s.id) as sales,
                       count(*) as lines, count(l.unit_cost) as costed_lines,
                       coalesce(sum(l.line_total) filter (where l.unit_cost is not null), 0) as costed_revenue,
                       coalesce(sum(l.quantity * l.unit_cost), 0) as cost_of_goods
                from sale_lines l join sales s on s.id = l.sale_id
                where l.product_id = :product and s.shop_id = :shop and s.voided = false
                """ + saleRange, params, (rs, i) -> {
            BigDecimal sold = rs.getBigDecimal("sold");
            BigDecimal revenue = rs.getBigDecimal("revenue");
            BigDecimal costedRevenue = rs.getBigDecimal("costed_revenue");
            BigDecimal costOfGoods = rs.getBigDecimal("cost_of_goods").setScale(2, RoundingMode.HALF_UP);
            BigDecimal grossProfit = costedRevenue.subtract(costOfGoods);
            BigDecimal margin = costedRevenue.signum() == 0 ? null
                    : grossProfit.multiply(BigDecimal.valueOf(100)).divide(costedRevenue, 1, RoundingMode.HALF_UP);
            BigDecimal average = sold.signum() == 0 ? null : revenue.divide(sold, 2, RoundingMode.HALF_UP);
            return new Summary(stock[0], sold, stock[1], stock[2], revenue, rs.getBigDecimal("discounts"), average,
                    rs.getLong("sales"), rs.getLong("costed_lines") == 0 ? null : grossProfit,
                    margin, rs.getLong("lines"), rs.getLong("costed_lines"));
        });
    }

    public record History(ProductInfo product, LocalDate from, LocalDate to, String type, int page, int pageSize,
                          long total, boolean hasNext, Summary summary, List<Row> rows) {
    }

    public record ProductInfo(Long id, String name, String unit, BigDecimal price, BigDecimal costPrice,
                              BigDecimal currentBalance) {
    }

    /** {@code kind} is LEDGER or PRICE_CHANGE; the fields that don't apply to a kind are null. */
    public record Row(String kind, Long id, Instant at, String type, BigDecimal quantity, BigDecimal balanceAfter,
                      String staffName, String note, Long receiptNumber, Long reversedMovementId,
                      BigDecimal price, BigDecimal costPrice, BigDecimal previousPrice, BigDecimal previousCost) {
    }

    /**
     * Stock movement for the range (received, written off, net change) and sales for the range
     * (sold, revenue, discounts, average selling price, number of sales, profit over costed lines).
     */
    public record Summary(BigDecimal received, BigDecimal sold, BigDecimal writtenOff, BigDecimal netChange,
                          BigDecimal revenue, BigDecimal discounts, BigDecimal averagePrice, long sales,
                          BigDecimal grossProfit, BigDecimal marginPercent, long saleLines, long costedLines) {
    }
}
