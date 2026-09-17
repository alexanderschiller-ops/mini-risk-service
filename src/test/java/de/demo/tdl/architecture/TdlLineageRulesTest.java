package de.demo.tdl.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TdlLineageRulesTest {
    private static final String MANUAL =
            "@TdlManual(source=\"a\", target=\"b\", type=TdlType.TRANSFORM) ";
    private static final String MAPPER =
            "@Mapper interface DemoMapper { @Mapping(source=\"a\", target=\"b\") Output map(Input source); }";

    @Test
    void acceptsUsedMapperIncludingMethodReference() {
        assertValid("@MicroService @LineageRelevant class Service { DemoMapper mapper; "
                + "Object run() { return values.stream().map(mapper::map); } }", MAPPER);
    }

    @Test
    void acceptsManualService() {
        assertValid("@MicroService @LineageRelevant " + MANUAL + "class Service {}");
    }

    @Test
    void acceptsManualMethod() {
        assertValid("@MicroService @LineageRelevant class Service { " + MANUAL + "void run() {} }");
    }

    @Test
    void acceptsExplicitExclusion() {
        assertValid("@MicroService @NotLineageRelevant(reason=\"Health check\") class Health {}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "@LineageRelevant @NotLineageRelevant(reason=\"reason\")"})
    void rejectsMissingOrConflictingClassification(String classification) {
        assertInvalid("genau eine", "@MicroService " + classification + " class Service {}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "(reason=\"\")", "(reason=\"   \")"})
    void rejectsMissingOrBlankReason(String argument) {
        assertInvalid("reason", "@MicroService @NotLineageRelevant" + argument + " class Service {}");
    }

    @Test
    void rejectsRelevantServiceWithoutEvidence() {
        assertInvalid("benoetigt", "@MicroService @LineageRelevant class Service {}", MAPPER);
    }

    @Test
    void unusedMapperDoesNotSupplyLineage() {
        assertInvalid("benoetigt", "@MicroService @LineageRelevant class Service { DemoMapper mapper; }", MAPPER);
    }

    @Test
    void acceptsOrdinaryUnmarkedClass() {
        assertValid("class Utility {}");
    }

    @Test
    void acceptsFullyQualifiedAnnotations() {
        assertValid("@de.demo.tdl.lineage.MicroService "
                + "@de.demo.tdl.lineage.NotLineageRelevant(reason=\"Health check\") class Health {}");
    }

    @Test
    void validatesRepeatableManualContainer() {
        assertValid("@MicroService @LineageRelevant @TdlManual.List({" + MANUAL.trim()
                + "}) class Service {}");
        assertInvalid("Pflichtattribut fehlt", "@MicroService @LineageRelevant "
                + "@TdlManual.List({@TdlManual(source=\"a\")}) class Service {}");
        assertInvalid("benoetigt", "@MicroService @LineageRelevant @TdlManual.List({}) class Service {}");
    }

    @Test
    void mappingsContainerCannotHideRiskyMapping() {
        assertInvalid("@TdlManual erforderlich", "@Mapper interface Fixture { "
                + "@Mappings({@Mapping(target=\"b\", expression=\"java(custom())\")}) Output map(Input a); }");
    }

    @Test
    void incompleteManualOnExcludedServiceStillFails() {
        assertInvalid("Pflichtattribut fehlt", "@MicroService @NotLineageRelevant(reason=\"Health check\") "
                + "@TdlManual(source=\"a\") class Health {}");
    }

    @Test
    void mapperWithCustomBodyRequiresManual() {
        assertInvalid("@TdlManual erforderlich", "@Mapper interface Fixture { "
                + "default Output map(Input a) { return custom(a); } }");
    }

    @ParameterizedTest
    @ValueSource(strings = {"expression", "defaultExpression", "conditionExpression",
            "qualifiedByName", "conditionQualifiedByName"})
    void riskyMappingRequiresManual(String attribute) {
        String method = "@Mapping(target=\"b\", " + attribute + "=\"custom\") Output map(Input source);";
        assertInvalid("@TdlManual erforderlich", "@Mapper interface MapperFixture { " + method + " }");
        assertValid("@Mapper interface MapperFixture { " + MANUAL + method + " }");
    }

    @Test
    void serviceManualDoesNotHideBrokenMapper() {
        assertInvalid("@TdlManual erforderlich",
                "@MicroService @LineageRelevant " + MANUAL + " class Service {}",
                "@Mapper interface Broken { @Mapping(target=\"b\", expression=\"java(custom())\") Output map(Input a); }");
    }

    @ParameterizedTest
    @ValueSource(strings = {"source=\"a\",target=\"b\"", "target=\"b\",type=TdlType.DIRECT",
            "source=\"a\",type=TdlType.DIRECT", "source=\" \",target=\"b\",type=TdlType.DIRECT",
            "source=\"a\",target=\"\",type=TdlType.DIRECT"})
    void incompleteManualRemainsAnError(String attributes) {
        assertInvalid("@TdlManual", "@MicroService @LineageRelevant @TdlManual(" + attributes + ") class Service {}");
        assertInvalid("@TdlManual", "@Mapper interface Fixture { @TdlManual(" + attributes + ") Output map(Input a); }");
    }

    private static void assertValid(String... sources) {
        var problems = TdlLineageArchitectureTest.analyzeSources(sources);
        assertTrue(problems.isEmpty(), problems::toString);
    }

    private static void assertInvalid(String message, String... sources) {
        var problems = TdlLineageArchitectureTest.analyzeSources(sources);
        assertTrue(problems.stream().anyMatch(p -> p.contains(message)), problems::toString);
    }
}
