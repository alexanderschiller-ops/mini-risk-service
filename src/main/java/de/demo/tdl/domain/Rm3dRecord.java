package de.demo.tdl.domain;

import java.math.BigDecimal;

public class Rm3dRecord {
    private String tradeId;
    private BigDecimal marketValue;

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    @Override
    public String toString() {
        return tradeId + ";" + marketValue.toPlainString();
    }
}
