package de.demo.tdl.service;

import de.demo.tdl.domain.DiscountRate;
import de.demo.tdl.domain.EnrichedSwapCashflow;
import de.demo.tdl.domain.ForwardRate;
import de.demo.tdl.domain.RiskPosition;
import de.demo.tdl.domain.SwapCashflow;
import de.demo.tdl.lineage.OpenLineageEmitter;
import de.demo.tdl.lineage.OpenLineageEmitter.DatasetRef;
import de.demo.tdl.mapper.RiskPositionMapper;
import de.demo.tdl.repository.InputDb;
import de.demo.tdl.repository.RiskPositionDb;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

// @tdl.job id=map_swap_cashflows name="Swap Cashflow Processing" description="Erzeugt Risikopositionen aus Swap-Cashflows"
// @tdl.input dataset=swap_cashflows
// @tdl.input dataset=forward_rates
// @tdl.input dataset=discount_rates
// @tdl.pipeline id=load_cashflows type=read input=swap_cashflows
// @tdl.pipeline id=enrich_rates type=join inputs=swap_cashflows,forward_rates,discount_rates
// @tdl.pipeline id=calculate_values type=sql sql=swap_valuation.sql
// @tdl.pipeline id=map_output type=mapping target=risk_positions
// @tdl.output dataset=risk_positions
public class SwapRiskProcessingService {

    private static final String JOB = "map_swap_cashflows";
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000000001");

    private final InputDb inputDb;
    private final RiskPositionDb riskDb;
    private final OpenLineageEmitter lineage;
    private final RiskPositionMapper mapper = Mappers.getMapper(RiskPositionMapper.class);

    private final List<DatasetRef> lineageInputs = List.of(
            DatasetRef.input("swap_cashflows"),
            DatasetRef.input("forward_rates"),
            DatasetRef.input("discount_rates")
    );
    private final List<DatasetRef> lineageOutputs =
            List.of(DatasetRef.risk("risk_positions"));

    public SwapRiskProcessingService(
            InputDb inputDb,
            RiskPositionDb riskDb,
            OpenLineageEmitter lineage) {
        this.inputDb = inputDb;
        this.riskDb = riskDb;
        this.lineage = lineage;
    }

    public List<RiskPosition> execute() {
        UUID runId = lineage.start(JOB, lineageInputs, lineageOutputs);

        try {
            List<SwapCashflow> cashflows = inputDb.swapCashflows();
            List<ForwardRate> forwards = inputDb.forwardRates();
            List<DiscountRate> discounts = inputDb.discountRates();

            List<RiskPosition> positions = cashflows.stream()
                    .map(cashflow -> enrichAndValue(cashflow, forwards, discounts))
                    .map(mapper::toRiskPosition)
                    .toList();

            riskDb.replaceAll(positions);
            lineage.complete(runId, JOB, lineageInputs, lineageOutputs);
            return positions;
        } catch (RuntimeException ex) {
            lineage.fail(runId, JOB, lineageInputs, lineageOutputs);
            throw ex;
        }
    }

    private EnrichedSwapCashflow enrichAndValue(
            SwapCashflow cashflow,
            List<ForwardRate> forwards,
            List<DiscountRate> discounts) {

        ForwardRate forward = forwards.stream()
                .filter(rate -> rate.rateDate().equals(cashflow.cashflowDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Kein Forward-/Referenzzins für " + cashflow.cashflowDate()));

        DiscountRate discount = discounts.stream()
                .filter(rate -> rate.rateDate().equals(cashflow.cashflowDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Keine Discount Rate für " + cashflow.cashflowDate()));

        BigDecimal payerCashflow = cashflow.nominal()
                .multiply(cashflow.fixedRate(), MC)
                .multiply(cashflow.accrualYears(), MC)
                .negate();

        BigDecimal receiverCashflow = cashflow.nominal()
                .multiply(forward.rate(), MC)
                .multiply(cashflow.accrualYears(), MC);

        BigDecimal netCashflow = payerCashflow.add(receiverCashflow, MC);

        BigDecimal discountBase = BigDecimal.ONE.add(
                discount.rate().multiply(cashflow.accrualYears(), MC), MC);

        BigDecimal discountFactor = BigDecimal.ONE.divide(
                discountBase.pow(cashflow.period(), MC), MC);

        BigDecimal presentValue = netCashflow
                .multiply(discountFactor, MC)
                .setScale(15, RoundingMode.HALF_UP);

        assertMatchesExcel("Payer CF", cashflow, payerCashflow, cashflow.expectedPayerCashflow());
        assertMatchesExcel("Receiver CF", cashflow, receiverCashflow, cashflow.expectedReceiverCashflow());
        assertMatchesExcel("Netto CF", cashflow, netCashflow, cashflow.expectedNetCashflow());

        return new EnrichedSwapCashflow(
                cashflow.tradeId(), cashflow.period(), cashflow.cashflowDate(),
                cashflow.currency(), cashflow.nominal(), forward.rate(), discount.rate(),
                payerCashflow, receiverCashflow, netCashflow, discountFactor, presentValue);
    }

    private void assertMatchesExcel(
            String field,
            SwapCashflow cashflow,
            BigDecimal calculated,
            BigDecimal expected) {
        if (calculated.subtract(expected).abs().compareTo(TOLERANCE) > 0) {
            throw new IllegalStateException(
                    field + " weicht in Periode " + cashflow.period()
                            + " vom Excel-Beispiel ab: calculated=" + calculated
                            + ", expected=" + expected);
        }
    }
}
