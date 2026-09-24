package de.demo.tdl.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Run against a PostgreSQL test database initialized using db/01-schema.sql and db/02-seed.sql. */
@EnabledIfEnvironmentVariable(named = "INPUT_DB_TEST_URL", matches = ".+")
class InputDbIntegrationTest {

    private final InputDb inputDb = new InputDb(new InputDbConfig(
            System.getenv("INPUT_DB_TEST_URL"),
            System.getenv().getOrDefault("INPUT_DB_TEST_USER", "inputdb"),
            System.getenv().getOrDefault("INPUT_DB_TEST_PASSWORD", "inputdb")));

    @Test
    void readsPostgresqlNumericDatesAndLeadingZeroIdsInDeterministicOrder() {
        var cashflows = inputDb.swapCashflows();
        assertEquals(20, cashflows.size());
        assertEquals(IntStream.rangeClosed(1, 20).boxed().toList(),
                cashflows.stream().map(row -> row.period()).toList());
        var first = cashflows.getFirst();
        assertEquals("00001", first.tradeId());
        assertEquals(LocalDate.of(2027, 2, 1), first.cashflowDate());
        assertEquals("EUR", first.currency());
        assertEquals(0, new BigDecimal("100000").compareTo(first.nominal()));
        assertEquals(0, new BigDecimal("0.04").compareTo(first.fixedRate()));
        assertEquals(0, new BigDecimal("0.5").compareTo(first.accrualYears()));
        assertEquals(0, new BigDecimal("-2000").compareTo(first.expectedPayerCashflow()));
        assertEquals(0, new BigDecimal("1000").compareTo(first.expectedReceiverCashflow()));
        assertEquals(0, new BigDecimal("-1000").compareTo(first.expectedNetCashflow()));
        assertEquals(cashflows, inputDb.swapCashflows());
    }

    @Test
    void readsBothRateTablesWithExactDecimals() {
        var forwards = inputDb.forwardRates();
        var discounts = inputDb.discountRates();
        assertEquals(20, forwards.size());
        assertEquals(20, discounts.size());
        assertEquals("6M Euribor", forwards.getFirst().curve());
        assertEquals("EUR IR ESTR", discounts.getFirst().curve());
        assertEquals(LocalDate.of(2027, 2, 1), forwards.getFirst().rateDate());
        assertEquals(LocalDate.of(2036, 8, 1), discounts.getLast().rateDate());
        assertEquals(0, new BigDecimal("0.020").compareTo(forwards.getFirst().rate()));
        assertEquals(0, new BigDecimal("0.030").compareTo(forwards.getLast().rate()));
        assertEquals(0, new BigDecimal("0.019").compareTo(discounts.getFirst().rate()));
        assertEquals(0, new BigDecimal("0.038").compareTo(discounts.getLast().rate()));
    }
}
