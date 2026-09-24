package de.demo.tdl.lineage;

import de.demo.tdl.repository.InputDbConfig;

/** Dataset identities used by the metadata export; contains no database credentials. */
public record TdlExportConfig(
        String inputDatabase,
        String environment,
        String riskPositionsDataset,
        String rm3dDataset) {

    public TdlExportConfig {
        requireValue(inputDatabase, "inputDatabase");
        requireValue(environment, "environment");
        requireValue(riskPositionsDataset, "riskPositionsDataset");
        requireValue(rm3dDataset, "rm3dDataset");
    }

    public static TdlExportConfig defaults() {
        return new TdlExportConfig("inputdb", "DEV",
                "runtime-output/risk_positions.csv", "travic-link/output/rm3d_output.rm3d");
    }

    public static TdlExportConfig fromEnvironment() {
        TdlExportConfig defaults = defaults();
        return new TdlExportConfig(InputDbConfig.fromEnvironment().databaseName(),
                setting("TDL_ENV", defaults.environment()),
                setting("TDL_RISK_DATASET", defaults.riskPositionsDataset()),
                setting("TDL_RM3D_DATASET", defaults.rm3dDataset()));
    }

    public String tableName(String table) {
        return inputDatabase + ".public." + table;
    }

    private static String setting(String key, String fallback) {
        String value = System.getProperty(key, System.getenv(key));
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
