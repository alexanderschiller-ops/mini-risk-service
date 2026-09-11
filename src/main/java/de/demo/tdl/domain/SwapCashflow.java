package de.demo.tdl.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SwapCashflow(
        String tradeId,
        int period,
        LocalDate cashflowDate,
        String currency,
        BigDecimal nominal,
        BigDecimal fixedRate,
        BigDecimal accrualYears,
        BigDecimal expectedPayerCashflow,
        BigDecimal expectedReceiverCashflow,
        BigDecimal expectedNetCashflow) {
}
