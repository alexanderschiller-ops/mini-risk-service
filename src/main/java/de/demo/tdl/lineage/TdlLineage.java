package de.demo.tdl.lineage;

import java.util.List;

/** Pipeline / DataFlow document containing separately identified DataJobs. */
public record TdlLineage(Pipeline pipeline) {

    public record Pipeline(String id, String name, String orchestrator, String platform,
                           String env, String description, List<Job> jobs) {
        public Pipeline {
            jobs = List.copyOf(jobs);
        }
    }

    public record Job(String id, String name, String type, String language, String description,
                      List<DatasetReference> inputs, List<DatasetReference> outputs,
                      List<ColumnLineage> columnLineage) {
        public Job {
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            columnLineage = List.copyOf(columnLineage);
        }
    }

    public record DatasetReference(String alias, String dataset, String platform, String env) {
        public static DatasetReference of(String alias, TdlDataset dataset) {
            return new DatasetReference(alias, dataset.name(), dataset.platform(), dataset.env());
        }
    }

    /** Field references use the documented alias.physical_column convention. */
    public record ColumnLineage(List<String> upstream, String downstream,
                                String transformType, String description) {
        public ColumnLineage {
            upstream = List.copyOf(upstream);
        }
    }
}
