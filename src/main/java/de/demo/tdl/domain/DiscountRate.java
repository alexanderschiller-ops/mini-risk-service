package de.demo.tdl.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountRate(
        String curve,
        LocalDate rateDate,
        BigDecimal rate) {
}
