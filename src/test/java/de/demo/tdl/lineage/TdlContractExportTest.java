package de.demo.tdl.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TdlContractExportTest {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<String> FILES = Set.of(
            "datasets/swap_cashflows.yaml", "datasets/forward_rates.yaml",
            "datasets/discount_rates.yaml", "datasets/risk_positions.yaml",
            "datasets/rm3d_output.yaml", "datajobs/mini-risk-pipeline.yaml");

    @TempDir
    Path tempDir;

    @Test
    void exportsDatasetsAndJobsWithResolvableColumnLineage() throws Exception {
        Path fresh = tempDir.resolve("export");
        TdlContractExporter.exportAll(fresh, TdlExportConfig.defaults());
        validateDocuments(fresh);
        JsonNode source = read(fresh, "datasets/swap_cashflows.yaml");
        assertEquals("postgres", source.path("platform").asText());
        assertEquals("inputdb.public.swap_cashflows", source.path("name").asText());
        assertEquals(10, source.path("schema").size());
        assertEquals(8, read(fresh, "datasets/risk_positions.yaml").path("schema").size());
        assertEquals(2, read(fresh, "datasets/rm3d_output.yaml").path("schema").size());
        JsonNode jobs = read(fresh, "datajobs/mini-risk-pipeline.yaml").path("pipeline").path("jobs");
        assertEquals("map_swap_cashflows", jobs.get(0).path("id").asText());
        assertEquals("risk_positions_to_rm3d", jobs.get(1).path("id").asText());
        JsonNode constant = findMapping(jobs.get(0), "risk.product_type");
        assertEquals("CONSTANT", constant.path("transform_type").asText());
        assertTrue(constant.path("upstream").isEmpty());
        assertTrue(constant.path("description").asText().contains("Plain Vanilla Swap"));
        assertEquals(Set.of("cashflows.nominal", "cashflows.fixed_rate", "cashflows.accrual_years",
                        "cashflows.period", "cashflows.cashflow_date", "forwards.rate", "forwards.rate_date",
                        "discounts.rate", "discounts.rate_date"),
                values(findMapping(jobs.get(0), "risk.present_value").path("upstream")));
        assertEquals(Set.of("risk.present_value"),
                values(findMapping(jobs.get(1), "rm3d.market_value").path("upstream")));

        // Preserve configured exports after Maven / Eclipse JUnit completes.
        Path output = Path.of("target", "tdl-output");
        TdlContractExporter.exportAll(output);
        validateDocuments(output);
        System.out.println("TDL-YAML-Ausleitung: " + output.toAbsolutePath());
    }

    @Test
    void customDatasetIdentitiesPropagateToEveryJobReference() throws Exception {
        var config = new TdlExportConfig("another_db", "TEST", "results/risk.csv", "results/out.rm3d");
        TdlContractExporter.exportAll(tempDir, config);
        validateDocuments(tempDir);
        assertEquals("another_db.public.forward_rates", read(tempDir, "datasets/forward_rates.yaml").path("name").asText());
        assertEquals("results/risk.csv", read(tempDir, "datasets/risk_positions.yaml").path("name").asText());
        assertEquals("TEST", read(tempDir, "datajobs/mini-risk-pipeline.yaml").path("pipeline").path("env").asText());
    }

    private void validateDocuments(Path directory) throws Exception {
        try (var files = Files.walk(directory)) {
            assertEquals(FILES, files.filter(Files::isRegularFile)
                    .map(directory::relativize).map(path -> path.toString().replace('\\', '/'))
                    .collect(Collectors.toSet()));
        }
        Map<String, JsonNode> datasets = new HashMap<>();
        for (String file : FILES) {
            if (!file.startsWith("datasets/")) continue;
            JsonNode dataset = read(directory, file);
            assertEquals(Set.of("platform", "name", "env", "description", "schema"), keys(dataset));
            assertFalse(dataset.path("name").asText().isBlank());
            assertFalse(dataset.path("env").asText().isBlank());
            assertTrue(Set.of("postgres", "file").contains(dataset.path("platform").asText()));
            assertNull(datasets.put(identity(dataset, "name"), dataset));
            Set<String> fields = new HashSet<>();
            for (JsonNode field : dataset.path("schema")) {
                assertEquals(Set.of("name", "type", "native_type", "nullable", "primary_key"), keys(field));
                assertTrue(fields.add(field.path("name").asText()));
                assertFalse(field.path("type").asText().isBlank());
                assertTrue(field.path("nullable").isBoolean());
                assertTrue(field.path("primary_key").isBoolean());
            }
        }
        assertEquals(5, datasets.size());
        JsonNode document = read(directory, "datajobs/mini-risk-pipeline.yaml");
        assertEquals(Set.of("pipeline"), keys(document));
        JsonNode pipeline = document.path("pipeline");
        assertEquals(Set.of("id", "name", "orchestrator", "platform", "env", "description", "jobs"), keys(pipeline));
        assertEquals(2, pipeline.path("jobs").size());
        for (JsonNode job : pipeline.path("jobs")) {
            assertEquals(Set.of("id", "name", "type", "language", "description", "inputs", "outputs", "column_lineage"), keys(job));
            assertEquals("BATCH", job.path("type").asText());
            Set<String> inputFields = referencedFields(job.path("inputs"), datasets);
            Set<String> outputFields = referencedFields(job.path("outputs"), datasets);
            Set<String> mappedOutputs = new HashSet<>();
            for (JsonNode mapping : job.path("column_lineage")) {
                assertEquals(Set.of("upstream", "downstream", "transform_type", "description"), keys(mapping));
                assertTrue(inputFields.containsAll(values(mapping.path("upstream"))), mapping.toString());
                assertTrue(outputFields.contains(mapping.path("downstream").asText()), mapping.toString());
                assertTrue(mappedOutputs.add(mapping.path("downstream").asText()));
            }
            assertEquals(outputFields, mappedOutputs, "Every physical output column needs lineage");
        }
    }

    private Set<String> referencedFields(JsonNode refs, Map<String, JsonNode> datasets) {
        Set<String> fields = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (JsonNode ref : refs) {
            assertEquals(Set.of("alias", "dataset", "platform", "env"), keys(ref));
            String alias = ref.path("alias").asText();
            assertTrue(aliases.add(alias));
            JsonNode dataset = datasets.get(identity(ref, "dataset"));
            assertNotNull(dataset, "Reference must match an exported dataset: " + ref);
            for (JsonNode field : dataset.path("schema")) fields.add(alias + "." + field.path("name").asText());
        }
        return fields;
    }

    private static String identity(JsonNode node, String name) {
        return node.path("platform").asText() + ":" + node.path(name).asText() + ":" + node.path("env").asText();
    }

    private JsonNode read(Path directory, String file) throws Exception {
        return YAML.readTree(directory.resolve(file).toFile());
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new HashSet<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static Set<String> values(JsonNode node) {
        Set<String> values = new HashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static JsonNode findMapping(JsonNode job, String downstream) {
        for (JsonNode mapping : job.path("column_lineage")) {
            if (mapping.path("downstream").asText().equals(downstream)) return mapping;
        }
        throw new AssertionError("Missing lineage for " + downstream);
    }
}
