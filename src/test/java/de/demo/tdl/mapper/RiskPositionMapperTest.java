package de.demo.tdl.mapper;

import de.demo.tdl.domain.EnrichedSwapCashflow;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskPositionMapperTest {
    @Test
    void preservesBusinessFieldsAcrossBothMappings() {
        var date = LocalDate.of(2026, 9, 24);
        var cashflow = new EnrichedSwapCashflow(
                "SWAP-42", 3, date, "EUR", new BigDecimal("1000000.00"),
                new BigDecimal("0.027"), new BigDecimal("0.031"),
                new BigDecimal("-15000"), new BigDecimal("13500"),
                new BigDecimal("-1500"), new BigDecimal("0.954"),
                new BigDecimal("-1431.000000000000000"));

        var position = Mappers.getMapper(RiskPositionMapper.class).toRiskPosition(cashflow);
        assertEquals("POS-SWAP-42-03", position.getPositionId());
        assertEquals("SWAP-42", position.getTradeId());
        assertEquals("Plain Vanilla Swap", position.getProductType());
        assertEquals(date, position.getCashflowDate());
        assertEquals(cashflow.nominal(), position.getNominal());
        assertEquals(cashflow.forwardRate(), position.getRate());
        assertEquals(cashflow.discountRate(), position.getDiscount());
        assertEquals(cashflow.presentValue(), position.getPresentValue());

        var rm3d = Mappers.getMapper(Rm3dMapper.class).toRm3d(position);
        assertEquals("SWAP-42;-1431.000000000000000", rm3d.toString());
    }
}
