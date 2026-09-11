package de.demo.tdl.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EnrichedSwapCashflow(
        String tradeId,
        int period,
        LocalDate cashflowDate,
        String currency,
        BigDecimal nominal,
        BigDecimal forwardRate,
        BigDecimal discountRate,
        BigDecimal payerCashflow,
        BigDecimal receiverCashflow,
        BigDecimal netCashflow,
        BigDecimal discountFactor,
        BigDecimal presentValue) {
}
