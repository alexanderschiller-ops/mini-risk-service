package de.demo.tdl.repository;

import de.demo.tdl.domain.RiskPosition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kleine Datei-basierte Simulation der RisikoPosDB.
 * Die fachlichen Objekte werden im Speicher gehalten und zusätzlich als CSV persistiert.
 */
public class RiskPositionDb {

    private final List<RiskPosition> rows = new ArrayList<>();
    private final Path storageFile;

    public RiskPositionDb(Path storageFile) {
        this.storageFile = storageFile;
    }

    public void replaceAll(List<RiskPosition> positions) {
        rows.clear();
        rows.addAll(positions);
        persist();
    }

    public List<RiskPosition> findAll() {
        return Collections.unmodifiableList(rows);
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        lines.add("position_id;trade_id;product_type;cashflow_date;nominal;rate;discount;present_value");

        for (RiskPosition position : rows) {
            lines.add(String.join(";",
                    position.getPositionId(),
                    position.getTradeId(),
                    position.getProductType(),
                    position.getCashflowDate().toString(),
                    position.getNominal().toPlainString(),
                    position.getRate().toPlainString(),
                    position.getDiscount().toPlainString(),
                    position.getPresentValue().toPlainString()));
        }

        try {
            Files.createDirectories(storageFile.getParent());
            Files.write(storageFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("RisikoPosDB-Simulation konnte nicht geschrieben werden", ex);
        }
    }
}
