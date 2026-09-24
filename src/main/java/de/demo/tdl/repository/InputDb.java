package de.demo.tdl.repository;

import de.demo.tdl.domain.DiscountRate;
import de.demo.tdl.domain.ForwardRate;
import de.demo.tdl.domain.SwapCashflow;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the source datasets from PostgreSQL. Schema and seed data are provisioned separately.
 */
public class InputDb {

    private final InputDbConfig config;

    public InputDb(InputDbConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public String databaseName() {
        return config.databaseName();
    }

    /** OpenLineage source namespace; contains neither credentials nor JDBC options. */
    public String namespace() {
        return config.namespace();
    }

    public String qualifiedTableName(String table) {
        if (!List.of("swap_cashflows", "forward_rates", "discount_rates").contains(table)) {
            throw new IllegalArgumentException("Unbekanntes Input-Dataset: " + table);
        }
        return databaseName() + ".public." + table;
    }

    public List<SwapCashflow> swapCashflows() {
        return read("""
                SELECT trade_id, period, cashflow_date, currency, nominal, fixed_rate,
                       accrual_years, expected_payer_cashflow, expected_receiver_cashflow,
                       expected_net_cashflow
                FROM public.swap_cashflows
                ORDER BY trade_id, period
                """, row -> new SwapCashflow(
                row.getString("trade_id"),
                row.getInt("period"),
                row.getObject("cashflow_date", LocalDate.class),
                row.getString("currency"),
                row.getBigDecimal("nominal"),
                row.getBigDecimal("fixed_rate"),
                row.getBigDecimal("accrual_years"),
                row.getBigDecimal("expected_payer_cashflow"),
                row.getBigDecimal("expected_receiver_cashflow"),
                row.getBigDecimal("expected_net_cashflow")));
    }

    public List<ForwardRate> forwardRates() {
        return read("""
                SELECT curve, rate_date, rate
                FROM public.forward_rates
                ORDER BY curve, rate_date
                """, row -> new ForwardRate(
                row.getString("curve"),
                row.getObject("rate_date", LocalDate.class),
                row.getBigDecimal("rate")));
    }

    public List<DiscountRate> discountRates() {
        return read("""
                SELECT curve, rate_date, rate
                FROM public.discount_rates
                ORDER BY curve, rate_date
                """, row -> new DiscountRate(
                row.getString("curve"),
                row.getObject("rate_date", LocalDate.class),
                row.getBigDecimal("rate")));
    }

    private <T> List<T> read(String sql, RowMapper<T> mapper) {
        try (Connection connection = config.openConnection()) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) {
                    result.add(mapper.map(rows));
                }
                return List.copyOf(result);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Input-Dataset konnte nicht aus PostgreSQL gelesen werden", ex);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }
}
