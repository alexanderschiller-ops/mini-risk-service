package de.demo.tdl.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RiskPosition {
    private String positionId;
    private String tradeId;
    private String productType;
    private LocalDate cashflowDate;
    private BigDecimal nominal;
    private BigDecimal rate;
    private BigDecimal discount;
    private BigDecimal presentValue;

    public String getPositionId() {
        return positionId;
    }

    public void setPositionId(String positionId) {
        this.positionId = positionId;
    }

    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public LocalDate getCashflowDate() {
        return cashflowDate;
    }

    public void setCashflowDate(LocalDate cashflowDate) {
        this.cashflowDate = cashflowDate;
    }

    public BigDecimal getNominal() {
        return nominal;
    }

    public void setNominal(BigDecimal nominal) {
        this.nominal = nominal;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getPresentValue() {
        return presentValue;
    }

    public void setPresentValue(BigDecimal presentValue) {
        this.presentValue = presentValue;
    }

    @Override
    public String toString() {
        return positionId + " | " + tradeId + " | " + cashflowDate + " | " + presentValue;
    }
}
