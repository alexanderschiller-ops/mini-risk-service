package de.demo.tdl;

import de.demo.tdl.domain.RiskPosition;
import de.demo.tdl.lineage.OpenLineageEmitter;
import de.demo.tdl.repository.InputDb;
import de.demo.tdl.repository.RiskPositionDb;
import de.demo.tdl.service.Rm3dOutputService;
import de.demo.tdl.service.SwapRiskProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void excelTestdatenLaufenDurchDieKomplettePipeline() throws Exception {
        Path riskFile = tempDir.resolve("risk_positions.csv");
        Path rm3dDir = tempDir.resolve("rm3d");

        InputDb inputDb = new InputDb(Path.of("data", "input"));
        RiskPositionDb riskDb = new RiskPositionDb(riskFile);

        List<RiskPosition> positions;

        try (OpenLineageEmitter lineage = new OpenLineageEmitter()) {
            positions = new SwapRiskProcessingService(inputDb, riskDb, lineage).execute();
            new Rm3dOutputService(riskDb, lineage).execute(rm3dDir);
        }

        assertEquals(20, positions.size());

        BigDecimal actualMarketValue = positions.stream()
                .map(RiskPosition::getPresentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal excelMarketValue = new BigDecimal("-6460.712349299317");
        assertTrue(
                actualMarketValue.subtract(excelMarketValue).abs()
                        .compareTo(new BigDecimal("0.000000001")) < 0,
                "Marktwert muss dem Excel-Beispiel entsprechen");

        assertEquals(21, Files.readAllLines(riskFile).size());
        assertEquals(21, Files.readAllLines(rm3dDir.resolve("rm3d_output.rm3d")).size());
    }
}
