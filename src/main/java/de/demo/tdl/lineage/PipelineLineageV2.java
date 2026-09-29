package de.demo.tdl.lineage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonSerialize(using = PipelineLineageV2.RequiredFieldsSerializer.class)
public class PipelineLineageV2 {

    @JsonProperty("$schema")
    private String schema;

    private PipelineConfig pipeline;

    public PipelineLineageV2(PipelineConfig pipeline) {
        this.pipeline = pipeline;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public PipelineConfig getPipeline() {
        return pipeline;
    }

    public void setPipeline(PipelineConfig pipeline) {
        this.pipeline = pipeline;
    }


    /** Sichtbarer Platzhalter; kein fachlich ermittelter Wert. */
    private static String unknown(String field) {
        return "__UNKNOWN_" + field + "__";
    }

    /**
     * Ergänzt beim Serialisieren alle zehn Pflichtfelder des Lineage-Schemas,
     * einschließlich bisher nicht verwendeter Objekttypen. Die Ergänzungen
     * betreffen nur den Output; die Listen und Objekte des Aufrufers bleiben
     * unverändert. Gilt für ObjectMapper.writeValueAsString(this), auch mit YAMLFactory.
     */
    public static final class RequiredFieldsSerializer
            extends JsonSerializer<PipelineLineageV2> {

        @Override
        public void serialize(PipelineLineageV2 value, JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            if (!(generator.getCodec() instanceof ObjectMapper)) {
                throw new IOException("PipelineLineageV2 benötigt einen ObjectMapper als Codec");
            }
            ObjectMapper mapper = (ObjectMapper) generator.getCodec();
            ObjectNode root = mapper.createObjectNode();
            if (value.schema != null) {
                root.put("$schema", value.schema);
            }
            // Nur PipelineConfig konvertieren: keine rekursive Serialisierung
            // von PipelineLineageV2 durch dessen eigenen Serializer.
            JsonNode tree = mapper.valueToTree(value.pipeline);
            ObjectNode pipeline = tree != null && tree.isObject()
                    ? (ObjectNode) tree : mapper.createObjectNode();
            root.set("pipeline", pipeline);
            requiredText(pipeline, "id", "PIPELINE_ID");
            requiredMetadata(pipeline);

            ArrayNode jobs = requiredArray(pipeline, "jobs");
            if (jobs.isEmpty()) {
                jobs.addObject().put("id", unknown("JOB_ID"));
            }
            for (int i = 0; i < jobs.size(); i++) {
                ObjectNode job = objectEntry(jobs, i, "jobs");
                requiredMetadata(job);
                ArrayNode inputs = requiredDatasets(job, "inputs");
                ArrayNode outputs = requiredDatasets(job, "outputs");

                // Alias und Referenz sind alternative Schema-Objekte.
                // Keinesfalls alias/dataset in ein DatasetRefConfig mischen.
                // Ein fehlender Alias wird als eigener Platzhalter-Input ergänzt.
                if (!hasField(inputs, "alias") && !hasField(outputs, "alias")) {
                    ObjectNode alias = inputs.addObject();
                    requiredText(alias, "alias", "DATASET_ALIAS");
                    requiredText(alias, "dataset", "ALIAS_DATASET");
                }
                if (!hasField(inputs, "name") && !hasField(outputs, "name")) {
                    requiredText(inputs.addObject(), "name", "DATASET_NAME");
                }

                ArrayNode columns = requiredArray(job, "column_lineage");
                if (columns.isEmpty()) {
                    columns.addObject();
                }
                for (int c = 0; c < columns.size(); c++) {
                    ObjectNode column = objectEntry(columns, c, "column_lineage");
                    requiredColumns(column, "upstream", "UPSTREAM");
                    requiredColumns(column, "downstream", "DOWNSTREAM");
                }
            }
            generator.writeTree(root);
        }

        private static void requiredMetadata(ObjectNode entity) {
            ArrayNode owners = requiredArray(entity, "owners");
            if (owners.isEmpty()) {
                owners.addObject();
            }
            for (int i = 0; i < owners.size(); i++) {
                requiredText(objectEntry(owners, i, "owners"), "id", "OWNER_ID");
            }
            JsonNode memory = entity.get("institutional_memory");
            if (memory == null || memory.isNull()) {
                memory = entity.putObject("institutional_memory");
            }
            if (!memory.isObject()) {
                throw new IllegalArgumentException("institutional_memory muss ein Objekt sein");
            }
            ArrayNode elements = requiredArray((ObjectNode) memory, "elements");
            if (elements.isEmpty()) {
                elements.addObject();
            }
            for (int i = 0; i < elements.size(); i++) {
                ObjectNode element = objectEntry(elements, i, "elements");
                requiredText(element, "title", "DOCUMENTATION_TITLE");
                requiredText(element, "url", "DOCUMENTATION_URL");
            }
        }

        private static ArrayNode requiredDatasets(ObjectNode job, String field) {
            ArrayNode datasets = requiredArray(job, field);
            if (datasets.isEmpty()) {
                datasets.addObject();
            }
            for (int i = 0; i < datasets.size(); i++) {
                JsonNode item = datasets.get(i);
                if (item != null && item.isTextual() && !item.textValue().isBlank()) {
                    continue; // String-Referenzen sind laut Schema zulässig.
                }
                if (item != null && item.isTextual()) {
                    datasets.set(i, datasets.objectNode());
                }
                ObjectNode dataset = objectEntry(datasets, i, field);
                if (dataset.has("alias") || dataset.has("dataset")) {
                    requiredText(dataset, "alias", "DATASET_ALIAS");
                    requiredText(dataset, "dataset", "ALIAS_DATASET");
                } else {
                    requiredText(dataset, "name", "DATASET_NAME");
                }
            }
            return datasets;
        }

        private static boolean hasField(ArrayNode array, String field) {
            for (JsonNode node : array) {
                if (node.isObject() && node.has(field)) {
                    return true;
                }
            }
            return false;
        }

        private static ArrayNode requiredArray(ObjectNode parent, String field) {
            JsonNode node = parent.get(field);
            if (node == null || node.isNull()) {
                return parent.putArray(field);
            }
            if (!node.isArray()) {
                throw new IllegalArgumentException(field + " muss eine Liste sein");
            }
            return (ArrayNode) node;
        }

        private static ObjectNode objectEntry(ArrayNode array, int index, String field) {
            JsonNode node = array.get(index);
            if (node == null || node.isNull()) {
                ObjectNode replacement = array.objectNode();
                array.set(index, replacement);
                return replacement;
            }
            if (!node.isObject()) {
                throw new IllegalArgumentException(field + "[" + index + "] muss ein Objekt sein");
            }
            return (ObjectNode) node;
        }

        private static void requiredText(ObjectNode object, String field, String label) {
            JsonNode node = object.get(field);
            if (node == null || node.isNull() || (node.isTextual() && node.textValue().isBlank())) {
                object.put(field, unknown(label));
            } else if (!node.isTextual()) {
                throw new IllegalArgumentException(field + " muss ein String sein");
            }
        }

        private static void requiredColumns(ObjectNode object, String field, String label) {
            JsonNode node = object.get(field);
            ArrayNode values;

            if (node == null || node.isNull()) {
                values = object.putArray(field);
            } else if (node.isArray()) {
                values = (ArrayNode) node;
            } else {
                throw new IllegalArgumentException(field + " muss eine Liste von Strings sein");
            }

            if (values.isEmpty()) {
                values.add(unknown(label));
            }

            for (int i = 0; i < values.size(); i++) {
                JsonNode item = values.get(i);
                if (item.isNull() || (item.isTextual() && item.textValue().isBlank())) {
                    values.set(i, values.textNode(unknown(label)));
                } else if (!item.isTextual()) {
                    throw new IllegalArgumentException(field + " darf nur Strings enthalten");
                }
            }
        }


    }

    // ---------------------------------------------------------
    // Pipeline
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PipelineConfig {

        private String orchestrator = "data-services";

        private String id;

        private String name;
        private String description;
        private String application;

        private List<String> tags;
        private List<String> terms;

        @JsonProperty("connection_type")
        private ConnectionType connectionType;

        private String env = "PROD";
        private String platform = "kafka";
        private String domain;

        private List<OwnerConfig> owners;
        private List<String> changes;

        @JsonProperty("documentation_links")
        private Map<String, URI> documentationLinks;

        @JsonProperty("institutional_memory")
        private InstitutionalMemoryConfig institutionalMemory;

        private List<JobConfig> jobs = new ArrayList<>();

        public PipelineConfig(String id) {
            this.id = id;
        }

        // Getter / Setter
        public String getOrchestrator() { return orchestrator; }
        public void setOrchestrator(String orchestrator) { this.orchestrator = orchestrator; }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getApplication() { return application; }
        public void setApplication(String application) { this.application = application; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public List<String> getTerms() { return terms; }
        public void setTerms(List<String> terms) { this.terms = terms; }

        public ConnectionType getConnectionType() { return connectionType; }
        public void setConnectionType(ConnectionType connectionType) { this.connectionType = connectionType; }

        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public List<OwnerConfig> getOwners() { return owners; }
        public void setOwners(List<OwnerConfig> owners) { this.owners = owners; }

        public List<String> getChanges() { return changes; }
        public void setChanges(List<String> changes) { this.changes = changes; }

        public Map<String, URI> getDocumentationLinks() { return documentationLinks; }
        public void setDocumentationLinks(Map<String, URI> documentationLinks) {
            this.documentationLinks = documentationLinks;
        }

        public InstitutionalMemoryConfig getInstitutionalMemory() {
            return institutionalMemory;
        }

        public void setInstitutionalMemory(
                InstitutionalMemoryConfig institutionalMemory) {
            this.institutionalMemory = institutionalMemory;
        }

        public List<JobConfig> getJobs() { return jobs; }
        public void setJobs(List<JobConfig> jobs) { this.jobs = jobs; }
    }


    // ---------------------------------------------------------
    // Job
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JobConfig {

        private String id = "main";
        private String name;
        private String type = "STREAMING";
        private String description;
        private String language;
        private String framework;
        private String application;

        @JsonProperty("external_url")
        private URI externalUrl;

        @JsonProperty("connection_type")
        private ConnectionType connectionType;

        private List<String> tags;
        private List<String> terms;
        private String domain;

        private List<OwnerConfig> owners;
        private List<String> changes;

        @JsonProperty("documentation_links")
        private Map<String, URI> documentationLinks;

        @JsonProperty("institutional_memory")
        private InstitutionalMemoryConfig institutionalMemory;

        /*
         * Schema erlaubt hier:
         * String
         * DatasetRefConfig
         * DatasetAliasConfig
         */
        private List<Object> inputs = new ArrayList<>();
        private List<Object> outputs = new ArrayList<>();

        @JsonProperty("column_lineage")
        private List<ColumnLineageConfig> columnLineage =
                new ArrayList<>();

        public JobConfig() {
        }

        public JobConfig(String id) {
            this.id = id;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }

        public String getApplication() { return application; }
        public void setApplication(String application) { this.application = application; }

        public URI getExternalUrl() { return externalUrl; }
        public void setExternalUrl(URI externalUrl) { this.externalUrl = externalUrl; }

        public ConnectionType getConnectionType() { return connectionType; }
        public void setConnectionType(ConnectionType connectionType) { this.connectionType = connectionType; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public List<String> getTerms() { return terms; }
        public void setTerms(List<String> terms) { this.terms = terms; }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public List<OwnerConfig> getOwners() { return owners; }
        public void setOwners(List<OwnerConfig> owners) { this.owners = owners; }

        public List<String> getChanges() { return changes; }
        public void setChanges(List<String> changes) { this.changes = changes; }

        public Map<String, URI> getDocumentationLinks() {
            return documentationLinks;
        }

        public void setDocumentationLinks(
                Map<String, URI> documentationLinks) {
            this.documentationLinks = documentationLinks;
        }

        public InstitutionalMemoryConfig getInstitutionalMemory() {
            return institutionalMemory;
        }

        public void setInstitutionalMemory(
                InstitutionalMemoryConfig institutionalMemory) {
            this.institutionalMemory = institutionalMemory;
        }

        public List<Object> getInputs() { return inputs; }
        public void setInputs(List<Object> inputs) { this.inputs = inputs; }

        public List<Object> getOutputs() { return outputs; }
        public void setOutputs(List<Object> outputs) { this.outputs = outputs; }

        public List<ColumnLineageConfig> getColumnLineage() {
            return columnLineage;
        }

        public void setColumnLineage(
                List<ColumnLineageConfig> columnLineage) {
            this.columnLineage = columnLineage;
        }
    }


    // ---------------------------------------------------------
    // Column Lineage
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ColumnLineageConfig {

        /*
         * Das Schema erlaubt String ODER Array<String>.
         * Im Java-Modell verwenden wir konsistent List<String>.
         */
        private List<String> downstream;
        private List<String> upstream;

        @JsonProperty("transform_type")
        private TransformType transformType;

        private String description;

        public ColumnLineageConfig(
                List<String> upstream,
                List<String> downstream) {

            this.upstream = upstream;

            this.downstream = downstream;
        }

        public List<String> getDownstream() { return downstream; }
        public void setDownstream(List<String> downstream) { this.downstream = downstream; }

        public List<String> getUpstream() { return upstream; }
        public void setUpstream(List<String> upstream) { this.upstream = upstream; }

        public TransformType getTransformType() { return transformType; }
        public void setTransformType(TransformType transformType) { this.transformType = transformType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }


    // ---------------------------------------------------------
    // Dataset Referenz
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DatasetRefConfig {

        private String name;
        private String platform;
        private String env;

        public DatasetRefConfig(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }

        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }
    }


    // ---------------------------------------------------------
    // Dataset Alias
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DatasetAliasConfig {

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String alias;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String dataset;
        private String platform;
        private String env;

        public DatasetAliasConfig(
                String alias,
                String dataset) {

            this.alias = alias;
            this.dataset = dataset;
        }

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }

        public String getDataset() { return dataset; }
        public void setDataset(String dataset) { this.dataset = dataset; }

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }

        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }
    }


    // ---------------------------------------------------------
    // Owner
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OwnerConfig {

        private String id;
        private String type = "TECHNICAL_OWNER";

        public OwnerConfig(String id) {
            this.id = id;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }


    // ---------------------------------------------------------
    // Institutional Memory
    // ---------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InstitutionalMemoryConfig {

        private List<InstitutionalMemoryElement> elements;

        public List<InstitutionalMemoryElement> getElements() {
            return elements;
        }

        public void setElements(
                List<InstitutionalMemoryElement> elements) {
            this.elements = elements;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InstitutionalMemoryElement {

        private String title;
        private URI url;
        private String description;

        public InstitutionalMemoryElement(
                String title,
                URI url) {

            this.title = title;
            this.url = url;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public URI getUrl() { return url; }
        public void setUrl(URI url) { this.url = url; }

        public String getDescription() { return description; }
        public void setDescription(String description) {
            this.description = description;
        }
    }



    // ---------------------------------------------------------
    // TransformType
    // ---------------------------------------------------------

    public enum TransformType {
        DIRECT,
        CONSTANT,
        EXPRESSION
    }


    // ---------------------------------------------------------
    // ConnectionType
    // ---------------------------------------------------------

    public enum ConnectionType {
        CONSUMER,
        PRODUCER,
        CONSUMER_PRODUCER,
        SOURCE_CONNECTOR,
        SINK_CONNECTOR,
        STREAMING_JOB
    }
}
