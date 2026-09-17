package de.demo.tdl.lineage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TdlContractExportTest {

    private static final Path OUTPUT_DIR = Path.of("target", "tdl-output");
    private static final Set<String> CONTRACTS = Set.of(
            "map_swap_cashflows.yaml",
            "risk_positions_to_rm3d.yaml",
            "risk_positions.yaml");

    @TempDir
    Path tempDir;

    @Test
    void exportsAndValidatesTdlContracts() throws Exception {
        // A fresh directory prevents old exports from hiding missing output.
        Path exportDir = tempDir.resolve("contracts");
        TdlContractExporter.exportAll(exportDir);

        try (var files = Files.list(exportDir)) {
            assertEquals(CONTRACTS, files.map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet()));
        }
        for (String contract : CONTRACTS) {
            try (var expected = getClass().getResourceAsStream("/contracts/" + contract)) {
                assertNotNull(expected, "Contract-Ressource fehlt: " + contract);
                assertArrayEquals(expected.readAllBytes(), Files.readAllBytes(exportDir.resolve(contract)),
                        "Exportierter Contract weicht von der Ressource ab: " + contract);
            }
        }

        // Keep the verified files available after Maven or Eclipse JUnit finishes.
        Files.createDirectories(OUTPUT_DIR);
        for (String contract : CONTRACTS) {
            Files.copy(exportDir.resolve(contract), OUTPUT_DIR.resolve(contract),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("TDL-YAML-Ausleitung aus Tests: " + OUTPUT_DIR.toAbsolutePath());
    }
}
