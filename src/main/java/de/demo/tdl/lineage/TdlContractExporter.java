package de.demo.tdl.lineage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import de.demo.tdl.lineage.TdlDataset.Field;
import de.demo.tdl.lineage.TdlLineage.ColumnLineage;
import de.demo.tdl.lineage.TdlLineage.DatasetReference;
import de.demo.tdl.lineage.TdlLineage.Job;
import de.demo.tdl.lineage.TdlLineage.Pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes dataset and DataJob metadata without opening a database connection. */
public final class TdlContractExporter {

    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private TdlContractExporter() {
    }

    public static void exportAll(Path outputDir) {
        exportAll(outputDir, TdlExportConfig.fromEnvironment());
    }

    public static void exportAll(Path outputDir, TdlExportConfig config) {
        try {
            for (Map.Entry<String, Object> document : documents(config).entrySet()) {
                Path target = outputDir.resolve(document.getKey());
                Files.createDirectories(target.getParent());
                YAML.writeValue(target.toFile(), document.getValue());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("TDL-YAMLs konnten nicht ausgeleitet werden", ex);
        }
    }

    /** Explicit definitions stay available even when PostgreSQL is offline. */
    static Map<String, Object> documents(TdlExportConfig config) {
        TdlDataset cashflows = new TdlDataset("postgres", config.tableName("swap_cashflows"),
                config.environment(), "Swap cashflows; expected_* columns verify the sample valuation.",
                List.of(field("trade_id", "string", "varchar(64)", true),
                        field("period", "number", "integer", true),
                        field("cashflow_date", "date", "date", false),
                        field("currency", "string", "varchar(3)", false),
                        field("nominal", "number", "numeric", false),
                        field("fixed_rate", "number", "numeric", false),
                        field("accrual_years", "number", "numeric", false),
                        field("expected_payer_cashflow", "number", "numeric", false),
                        field("expected_receiver_cashflow", "number", "numeric", false),
                        field("expected_net_cashflow", "number", "numeric", false)));
        TdlDataset forwards = rateDataset(config, "forward_rates", "Forward reference rates.");
        TdlDataset discounts = rateDataset(config, "discount_rates", "Discount rates.");
        TdlDataset risk = new TdlDataset("file", config.riskPositionsDataset(), config.environment(),
                "Risk positions persisted as a semicolon-separated CSV by the RiskPositionDb simulation.",
                List.of(field("position_id", "string", "text", true),
                        field("trade_id", "string", "text", false),
                        field("product_type", "string", "text", false),
                        field("cashflow_date", "date", "ISO-8601 date text", false),
                        field("nominal", "number", "decimal text", false),
                        field("rate", "number", "decimal text", false),
                        field("discount", "number", "decimal text", false),
                        field("present_value", "number", "decimal text", false)));
        TdlDataset rm3d = new TdlDataset("file", config.rm3dDataset(), config.environment(),
                "RM3D output: trade_id and market_value, separated by semicolons.",
                List.of(field("trade_id", "string", "text", false),
                        field("market_value", "number", "decimal text", false)));

        Job valuation = new Job("map_swap_cashflows", "Swap Cashflow Processing", "BATCH", "JAVA",
                "Reads PostgreSQL inputs, selects rates by cashflow_date, values cashflows and writes risk positions.",
                List.of(ref("cashflows", cashflows), ref("forwards", forwards), ref("discounts", discounts)),
                List.of(ref("risk", risk)), List.of(
                lineage("risk.position_id", "TRANSFORM", "String.format('POS-%s-%02d', trade_id, period).",
                        "cashflows.trade_id", "cashflows.period"),
                lineage("risk.trade_id", "IDENTITY", "Copy trade identifier.", "cashflows.trade_id"),
                lineage("risk.product_type", "CONSTANT", "Literal 'Plain Vanilla Swap'; no source column."),
                lineage("risk.cashflow_date", "IDENTITY", "Copy payment date.", "cashflows.cashflow_date"),
                lineage("risk.nominal", "IDENTITY", "Copy nominal amount.", "cashflows.nominal"),
                lineage("risk.rate", "LOOKUP", "Select forward rate where rate_date equals cashflow_date.",
                        "forwards.rate", "forwards.rate_date", "cashflows.cashflow_date"),
                lineage("risk.discount", "LOOKUP", "Select discount rate where rate_date equals cashflow_date.",
                        "discounts.rate", "discounts.rate_date", "cashflows.cashflow_date"),
                lineage("risk.present_value", "TRANSFORM",
                        "nominal * (forward_rate - fixed_rate) * accrual_years / "
                                + "(1 + discount_rate * accrual_years)^period; HALF_UP to 15 decimal places. "
                                + "Rates are selected by cashflow_date.",
                        "cashflows.nominal", "cashflows.fixed_rate", "cashflows.accrual_years", "cashflows.period",
                        "cashflows.cashflow_date", "forwards.rate", "forwards.rate_date",
                        "discounts.rate", "discounts.rate_date")));
        Job delivery = new Job("risk_positions_to_rm3d", "RM3D Output Transformation", "BATCH", "JAVA",
                "Maps the persisted risk-position fields to the two-column RM3D output.",
                List.of(ref("risk", risk)), List.of(ref("rm3d", rm3d)), List.of(
                lineage("rm3d.trade_id", "IDENTITY", "Copy trade identifier.", "risk.trade_id"),
                lineage("rm3d.market_value", "IDENTITY", "Write present_value as market_value without aggregation.",
                        "risk.present_value")));

        Map<String, Object> documents = new LinkedHashMap<>();
        documents.put("datasets/swap_cashflows.yaml", cashflows);
        documents.put("datasets/forward_rates.yaml", forwards);
        documents.put("datasets/discount_rates.yaml", discounts);
        documents.put("datasets/risk_positions.yaml", risk);
        documents.put("datasets/rm3d_output.yaml", rm3d);
        documents.put("datajobs/mini-risk-pipeline.yaml", new TdlLineage(new Pipeline(
                "mini-risk-service", "Mini Risk Pipeline", "data-services", "postgres", config.environment(),
                "PostgreSQL cashflows to risk-position CSV and RM3D. Field references use alias.physical_column; "
                        + "this convention must match the importing lineage parser. CONSTANT mappings have empty upstream lists.",
                List.of(valuation, delivery))));
        return documents;
    }

    private static TdlDataset rateDataset(TdlExportConfig config, String table, String description) {
        return new TdlDataset("postgres", config.tableName(table), config.environment(), description,
                List.of(field("curve", "string", "varchar(128)", true),
                        field("rate_date", "date", "date", true),
                        field("rate", "number", "numeric", false)));
    }

    private static Field field(String name, String type, String nativeType, boolean primaryKey) {
        return new Field(name, type, nativeType, false, primaryKey);
    }

    private static DatasetReference ref(String alias, TdlDataset dataset) {
        return DatasetReference.of(alias, dataset);
    }

    private static ColumnLineage lineage(String downstream, String type, String description, String... upstream) {
        return new ColumnLineage(List.of(upstream), downstream, type, description);
    }
}
