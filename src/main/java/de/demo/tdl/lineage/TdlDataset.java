package de.demo.tdl.lineage;

import java.util.List;

/** One dataset document, independent of the jobs that read or produce it. */
public record TdlDataset(
        String platform,
        String name,
        String env,
        String description,
        List<Field> schema) {

    public TdlDataset {
        schema = List.copyOf(schema);
    }

    public record Field(String name, String type, String nativeType,
                        boolean nullable, boolean primaryKey) {
    }
}
