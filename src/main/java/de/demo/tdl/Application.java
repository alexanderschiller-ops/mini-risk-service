package de.demo.tdl;

import de.demo.tdl.domain.RiskPosition;
import de.demo.tdl.lineage.OpenLineageEmitter;
import de.demo.tdl.lineage.TdlContractExporter;
import de.demo.tdl.repository.InputDb;
import de.demo.tdl.repository.RiskPositionDb;
import de.demo.tdl.service.Rm3dOutputService;
import de.demo.tdl.service.SwapRiskProcessingService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Application {
    public static void main(String[] args) throws Exception {
        Path inputDir = args.length > 0 ? Path.of(args[0]) : Path.of("data", "input");
        Path runtimeDir = Path.of("runtime-output");
        Path rm3dDir = Path.of("travic-link", "output");
        Path tdlDir = Path.of("tdl-output");

        Files.createDirectories(runtimeDir);
        Files.createDirectories(rm3dDir);
        Files.createDirectories(tdlDir);
        TdlContractExporter.exportAll(tdlDir);

        InputDb inputDb = new InputDb(inputDir);
        RiskPositionDb riskDb = new RiskPositionDb(runtimeDir.resolve("risk_positions.csv"));

        List<RiskPosition> positions;
        Path rm3dFile;
        try (OpenLineageEmitter lineage = new OpenLineageEmitter()) {
            positions = new SwapRiskProcessingService(inputDb, riskDb, lineage).execute();
            rm3dFile = new Rm3dOutputService(riskDb, lineage).execute(rm3dDir);
        }

        BigDecimal marketValue = positions.stream()
                .map(RiskPosition::getPresentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        System.out.println("Risikopositionen: " + positions.size());
        System.out.println("Marktwert des Swaps: " + marketValue.toPlainString());
        System.out.println("RisikoPosDB-Simulation: " + runtimeDir.resolve("risk_positions.csv").toAbsolutePath());
        System.out.println("RM3D-Datei: " + rm3dFile.toAbsolutePath());
        System.out.println("YAML-Ausleitung: " + tdlDir.toAbsolutePath());
    }
}
