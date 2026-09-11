package de.demo.tdl.lineage;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.client.OpenLineage.Job;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.Run;
import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.OpenLineageClient;
import io.openlineage.client.transports.ConsoleTransport;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Technische Data Lineage über den OpenLineage Java Client.
 * Im Demo werden die Events per ConsoleTransport ausgegeben.
 */
public class OpenLineageEmitter implements AutoCloseable {

    public static final String JOB_NAMESPACE = "risk-demo";
    public static final String INPUT_NAMESPACE = "inputdb://NONAME";
    public static final String RISK_NAMESPACE = "riskdb://public";
    public static final String FILE_NAMESPACE = "file://travic-link/output";

    private final OpenLineage ol =
            new OpenLineage(URI.create("https://example.local/mini-risk-service"));
    private final OpenLineageClient client;

    public OpenLineageEmitter() {
        this.client = OpenLineageClient.builder()
                .transport(new ConsoleTransport())
                .build();
    }

    public UUID start(String jobName, List<DatasetRef> inputs, List<DatasetRef> outputs) {
        UUID runId = UUID.randomUUID();
        emit(RunEvent.EventType.START, runId, jobName, inputs, outputs);
        return runId;
    }

    public void complete(UUID runId, String jobName, List<DatasetRef> inputs, List<DatasetRef> outputs) {
        emit(RunEvent.EventType.COMPLETE, runId, jobName, inputs, outputs);
    }

    public void fail(UUID runId, String jobName, List<DatasetRef> inputs, List<DatasetRef> outputs) {
        emit(RunEvent.EventType.FAIL, runId, jobName, inputs, outputs);
    }

    private void emit(
            RunEvent.EventType eventType,
            UUID runId,
            String jobName,
            List<DatasetRef> inputRefs,
            List<DatasetRef> outputRefs) {

        Run run = ol.newRunBuilder()
                .runId(runId)
                .facets(ol.newRunFacetsBuilder().build())
                .build();

        Job job = ol.newJobBuilder()
                .namespace(JOB_NAMESPACE)
                .name(jobName)
                .facets(ol.newJobFacetsBuilder().build())
                .build();

        List<InputDataset> inputs = inputRefs.stream()
                .map(ref -> ol.newInputDatasetBuilder()
                        .namespace(ref.namespace())
                        .name(ref.name())
                        .facets(ol.newDatasetFacetsBuilder().build())
                        .inputFacets(ol.newInputDatasetInputFacetsBuilder().build())
                        .build())
                .toList();

        List<OutputDataset> outputs = outputRefs.stream()
                .map(ref -> ol.newOutputDatasetBuilder()
                        .namespace(ref.namespace())
                        .name(ref.name())
                        .facets(ol.newDatasetFacetsBuilder().build())
                        .outputFacets(ol.newOutputDatasetOutputFacetsBuilder().build())
                        .build())
                .toList();

        RunEvent event = ol.newRunEventBuilder()
                .eventType(eventType)
                .eventTime(ZonedDateTime.now(ZoneOffset.UTC))
                .run(run)
                .job(job)
                .inputs(inputs)
                .outputs(outputs)
                .build();

        client.emit(event);
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    public record DatasetRef(String namespace, String name) {
        public static DatasetRef input(String name) {
            return new DatasetRef(INPUT_NAMESPACE, name);
        }

        public static DatasetRef risk(String name) {
            return new DatasetRef(RISK_NAMESPACE, name);
        }

        public static DatasetRef file(String name) {
            return new DatasetRef(FILE_NAMESPACE, name);
        }
    }
}
