package de.demo.tdl.service;

import de.demo.tdl.lineage.MicroService;
import de.demo.tdl.lineage.LineageRelevant;

import de.demo.tdl.domain.Rm3dRecord;
import de.demo.tdl.lineage.OpenLineageEmitter;
import de.demo.tdl.lineage.OpenLineageEmitter.DatasetRef;
import de.demo.tdl.mapper.Rm3dMapper;
import de.demo.tdl.repository.RiskPositionDb;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// @tdl.job id=risk_positions_to_rm3d name="RM3D Output Transformation" inputStyle=single outputStyle=single
// @tdl.input dataset=risk_positions
// @tdl.output dataset=rm3d_output
// @tdl.pipeline id=map_rm3d_format type=mapping description="Überführung in das rm3d-Zielformat"
// @tdl.target type=file format=rm3d location=travic-link/output
@MicroService
@LineageRelevant
public class Rm3dOutputService {

    private static final String JOB = "risk_positions_to_rm3d";

    private final RiskPositionDb riskDb;
    private final OpenLineageEmitter lineage;
    private final Rm3dMapper mapper = Mappers.getMapper(Rm3dMapper.class);

    private final List<DatasetRef> lineageInputs =
            List.of(DatasetRef.risk("risk_positions"));
    private final List<DatasetRef> lineageOutputs =
            List.of(DatasetRef.file("rm3d_output"));

    public Rm3dOutputService(RiskPositionDb riskDb, OpenLineageEmitter lineage) {
        this.riskDb = riskDb;
        this.lineage = lineage;
    }

    public Path execute(Path outputDir) {
        UUID runId = lineage.start(JOB, lineageInputs, lineageOutputs);

        try {
            List<Rm3dRecord> records = riskDb.findAll().stream()
                    .map(mapper::toRm3d)
                    .toList();

            Files.createDirectories(outputDir);
            Path target = outputDir.resolve("rm3d_output.rm3d");

            List<String> lines = new ArrayList<>();
            lines.add("trade_id;market_value");
            records.stream().map(Rm3dRecord::toString).forEach(lines::add);
            Files.write(target, lines, StandardCharsets.UTF_8);

            lineage.complete(runId, JOB, lineageInputs, lineageOutputs);
            return target;
        } catch (IOException ex) {
            lineage.fail(runId, JOB, lineageInputs, lineageOutputs);
            throw new UncheckedIOException("RM3D-Ausgabe konnte nicht geschrieben werden", ex);
        } catch (RuntimeException ex) {
            lineage.fail(runId, JOB, lineageInputs, lineageOutputs);
            throw ex;
        }
    }
}
