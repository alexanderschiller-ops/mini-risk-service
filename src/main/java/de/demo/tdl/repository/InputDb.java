package de.demo.tdl.repository;

import de.demo.tdl.domain.DiscountRate;
import de.demo.tdl.domain.ForwardRate;
import de.demo.tdl.domain.SwapCashflow;
import de.demo.tdl.util.CsvSupport;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Simuliert die externen InputDB-Datasets mit CSV-Dateien,
 * die aus dem bereitgestellten Excel-Beispiel abgeleitet wurden.
 */
public class InputDb {

    private final Path inputDir;

    public InputDb(Path inputDir) {
        this.inputDir = inputDir;
    }

    public List<SwapCashflow> swapCashflows() {
        return CsvSupport.readSemicolonFile(inputDir.resolve("swap_cashflows.csv")).stream()
                .map(c -> new SwapCashflow(
                        c[0],
                        Integer.parseInt(c[1]),
                        LocalDate.parse(c[2]),
                        c[3],
                        new BigDecimal(c[4]),
                        new BigDecimal(c[5]),
                        new BigDecimal(c[6]),
                        new BigDecimal(c[7]),
                        new BigDecimal(c[8]),
                        new BigDecimal(c[9])))
                .toList();
    }

    public List<ForwardRate> forwardRates() {
        return CsvSupport.readSemicolonFile(inputDir.resolve("forward_reference_rates.csv")).stream()
                .map(c -> new ForwardRate(
                        c[0],
                        LocalDate.parse(c[1]),
                        new BigDecimal(c[2])))
                .toList();
    }

    public List<DiscountRate> discountRates() {
        return CsvSupport.readSemicolonFile(inputDir.resolve("discount_rates.csv")).stream()
                .map(c -> new DiscountRate(
                        c[0],
                        LocalDate.parse(c[1]),
                        new BigDecimal(c[2])))
                .toList();
    }
}
