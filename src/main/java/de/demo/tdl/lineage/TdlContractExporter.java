package de.demo.tdl.lineage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class TdlContractExporter {

    private static final List<String> CONTRACTS = List.of(
            "map_swap_cashflows.yaml",
            "risk_positions_to_rm3d.yaml",
            "risk_positions.yaml"
    );

    private TdlContractExporter() {
    }

    public static void exportAll(Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            for (String fileName : CONTRACTS) {
                String resource = "/contracts/" + fileName;

                try (InputStream input = TdlContractExporter.class.getResourceAsStream(resource)) {
                    if (input == null) {
                        throw new IllegalStateException("Contract-Ressource fehlt: " + resource);
                    }

                    Files.copy(
                            input,
                            outputDir.resolve(fileName),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("TDL-YAMLs konnten nicht ausgeleitet werden", ex);
        }
    }
}
