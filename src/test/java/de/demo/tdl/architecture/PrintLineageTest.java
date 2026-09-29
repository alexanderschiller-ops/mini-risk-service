package de.demo.tdl.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.demo.tdl.lineage.PipelineLineage;
import de.demo.tdl.lineage.PipelineLineage.ColumnLineageConfig;
import de.demo.tdl.lineage.PipelineLineage.DatasetRefConfig;
import de.demo.tdl.lineage.PipelineLineage.JobConfig;
import de.demo.tdl.lineage.PipelineLineage.PipelineConfig;

import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

//
class PrintLineageTest {

    @Test
    void printLineageYaml() throws Exception {
    	
    	//Liste von Mapstruct Intercaces mit @Mapper-Annotations
        List<PipelineLineage> result =
                new ArrayList<>();

        JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(
                             null,
                             null,
                             null)) {

            List<File> sourceFiles;

            /*
             * Alle Java-Dateien unter src/main/java lesen.
             */
            try (var files =
                         Files.walk(
                                 Path.of(
                                         "src",
                                         "main",
                                         "java"))) {

                sourceFiles =
                        files
                                .filter(Files::isRegularFile)
                                .filter(path ->
                                        path.toString()
                                                .endsWith(".java"))
                                .map(Path::toFile)
                                .toList();
            }

            var compilationUnits =
                    fileManager
                            .getJavaFileObjectsFromFiles(
                                    sourceFiles);

            /*
             * Nur Annotation Processing.
             * Es werden keine Klassen erzeugt.
             */
            var task =
                    compiler.getTask(
                            null,
                            fileManager,
                            null,
                            List.of(
                                    "-proc:only",
                                    "-classpath",
                                    System.getProperty(
                                            "java.class.path")
                            ),
                            null,
                            compilationUnits
                    );

            /*
             * Unser eigener Processor.
             */
            task.setProcessors(
                    List.of(
                            new LineageProcessor(
                                    result)));

            boolean success =
                    task.call();

            assertTrue(
                    success,
                    "Analyse der Mapper ist fehlgeschlagen");
        }

        /*
         * Es muss mindestens ein @Mapper gefunden werden.
         */
        assertFalse(
                result.isEmpty(),
                "Keine @Mapper Interfaces gefunden");


        /*
         * YAML Mapper.
         */
        ObjectMapper yamlMapper =
                new ObjectMapper(
                        new YAMLFactory());


        /*
         * Zielordner:
         *
         * <Projekt>/tdl-output
         */
        Path outputDir =
                Path.of("tdl-output");

        Files.createDirectories(
                outputDir);


        /*
         * Pro @Mapper wird eine YAML-Datei geschrieben.
         */
        for (PipelineLineage lineage : result) {

            String pipelineId =
                    lineage
                            .getPipeline()
                            .getId();

            Path outputFile =
                    outputDir.resolve(
                            pipelineId + ".yaml"
                    );


            yamlMapper.writeValue(
                    outputFile.toFile(),
                    lineage
            );


            System.out.println(
                    "TDL YAML erzeugt: "
                            + outputFile
                                    .toAbsolutePath()
            );
        }
    }


    // =========================================================
    // Annotation Processor
    // =========================================================

    static class LineageProcessor
            extends AbstractProcessor {

        private final List<PipelineLineage> result;


        LineageProcessor(
                List<PipelineLineage> result) {

            this.result = result;
        }


        @Override
        public Set<String>
        getSupportedAnnotationTypes() {

            /*
             * Nur MapStruct @Mapper.
             */
            return Set.of(
                    Mapper.class.getName()
            );
        }


        @Override
        public SourceVersion
        getSupportedSourceVersion() {

            return SourceVersion.latestSupported();
        }


        @Override
        public boolean process(
                Set<? extends TypeElement> annotations,
                RoundEnvironment roundEnvironment) {

            TypeElement mapperAnnotation =
                    processingEnv
                            .getElementUtils()
                            .getTypeElement(
                                    Mapper.class.getName()
                            );


            if (mapperAnnotation == null) {

                return false;
            }


            /*
             * Nur Interfaces betrachten,
             * auf denen @Mapper steht.
             */
            for (Element element :
                    roundEnvironment
                            .getElementsAnnotatedWith(
                                    mapperAnnotation)) {


                if (element.getKind()
                        != ElementKind.INTERFACE) {

                    continue;
                }


                TypeElement mapper =
                        (TypeElement) element;


                result.add(
                        createPipeline(
                                mapper));
            }


            return false;
        }


        // =====================================================
        // Pipeline erzeugen
        // =====================================================

        private PipelineLineage createPipeline(
                TypeElement mapper) {

            /*
             * PipelineConfig.id ist Pflicht.
             *
             * Dafür wird direkt der Name des
             * @Mapper Interfaces verwendet.
             *
             * Beispiel:
             *
             * RiskPositionMapper
             */
            String pipelineId =
                    mapper
                            .getSimpleName()
                            .toString();


            PipelineConfig pipeline =
                    new PipelineConfig(
                            pipelineId
                    );


            /*
             * Ausschließlich Methoden innerhalb
             * dieses @Mapper Interfaces.
             */
            for (Element element :
                    mapper.getEnclosedElements()) {


                if (element.getKind()
                        != ElementKind.METHOD) {

                    continue;
                }


                ExecutableElement method =
                        (ExecutableElement) element;


                /*
                 * Default-Methoden und andere
                 * implementierte Helper ignorieren.
                 *
                 * Nur abstrakte MapStruct
                 * Mapping-Methoden verwenden.
                 */
                if (!method
                        .getModifiers()
                        .contains(
                                Modifier.ABSTRACT)) {

                    continue;
                }


                /*
                 * void Methoden ignorieren.
                 */
                if (method
                        .getReturnType()
                        .getKind()
                        .name()
                        .equals("VOID")) {

                    continue;
                }


                pipeline
                        .getJobs()
                        .add(
                                createJob(
                                        method));
            }


            /*
             * Pflichtfeld:
             *
             * LineageSchema.pipeline
             */
            return new PipelineLineage(
                    pipeline
            );
        }


        // =====================================================
        // Job erzeugen
        // =====================================================

        private JobConfig createJob(
                ExecutableElement method) {

            /*
             * Job-ID kommt direkt vom
             * Mapper-Methodennamen.
             *
             * Beispiel:
             *
             * toRiskPosition
             */
            JobConfig job =
                    new JobConfig(
                            method
                                    .getSimpleName()
                                    .toString()
                    );


            /*
             * ==============================
             * INPUTS
             * ==============================
             *
             * Jeder Methodenparameter wird
             * DatasetRefConfig.
             *
             * DatasetRefConfig.name
             * ist Pflicht.
             */
            for (VariableElement parameter :
                    method.getParameters()) {


                String inputName =
                        simpleTypeName(
                                parameter.asType());


                job
                        .getInputs()
                        .add(
                                new DatasetRefConfig(
                                        inputName));
            }


            /*
             * ==============================
             * OUTPUT
             * ==============================
             *
             * Return-Type der Mapper-Methode
             * wird Output.
             */
            String outputName =
                    simpleTypeName(
                            method
                                    .getReturnType());


            job
                    .getOutputs()
                    .add(
                            new DatasetRefConfig(
                                    outputName));


            /*
             * ==============================
             * IMPLIZITE MAPPINGS
             * ==============================
             *
             * MapStruct mappt gleichnamige
             * Felder automatisch.
             */
            Map<String, ColumnLineageConfig>
                    mappings =
                    implicitMappings(
                            method);


            /*
             * ==============================
             * EXPLIZITE @Mapping
             * ==============================
             *
             * Diese überschreiben gegebenenfalls
             * das implizite Mapping.
             */
            Mapping[] explicitMappings =
                    method
                            .getAnnotationsByType(
                                    Mapping.class);


            for (Mapping mapping :
                    explicitMappings) {


                ColumnLineageConfig column =
                        explicitMapping(
                                mapping);


                mappings.put(
                        mapping.target(),
                        column
                );
            }


            /*
             * Alle gefundenen Spalten-Lineages
             * in das YAML-Zielmodell übernehmen.
             */
            job
                    .getColumnLineage()
                    .addAll(
                            mappings.values());


            return job;
        }


        // =====================================================
        // Implizite MapStruct-Mappings
        // =====================================================

        private Map<String, ColumnLineageConfig>
        implicitMappings(
                ExecutableElement method) {

            Map<String, ColumnLineageConfig> lineage =
                    new LinkedHashMap<>();


            /*
             * Aktuell unterstützen wir das
             * einfache MapStruct-Muster:
             *
             * Target map(Source source)
             */
            if (method
                    .getParameters()
                    .size() != 1) {

                return lineage;
            }


            VariableElement sourceParameter =
                    method
                            .getParameters()
                            .get(0);


            TypeMirror sourceType =
                    sourceParameter
                            .asType();


            TypeMirror targetType =
                    method
                            .getReturnType();


            Set<String> sourceFields =
                    fieldsOf(
                            sourceType);


            Set<String> targetFields =
                    fieldsOf(
                            targetType);


            /*
             * Gleicher Feldname
             *
             * Source.tradeId
             *        ↓
             * Target.tradeId
             */
            for (String target :
                    targetFields) {


                if (sourceFields
                        .contains(target)) {


                    /*
                     * Pflichtfelder:
                     *
                     * upstream
                     * downstream
                     */
                    ColumnLineageConfig column =
                            new ColumnLineageConfig(
                                    target,
                                    target
                            );


                    column.setTransformType(
                            "DIRECT"
                    );


                    lineage.put(
                            target,
                            column
                    );
                }
            }


            return lineage;
        }


        // =====================================================
        // Explizites MapStruct @Mapping
        // =====================================================

        private ColumnLineageConfig explicitMapping(
                Mapping mapping) {


            /*
             * downstream kommt direkt aus
             *
             * mapping.target()
             */
            String downstream =
                    mapping.target();


            /*
             * =================================
             * SOURCE
             * =================================
             *
             * @Mapping(
             *     source = "forwardRate",
             *     target = "rate"
             * )
             */
            if (!mapping
                    .source()
                    .isBlank()) {


                ColumnLineageConfig column =
                        new ColumnLineageConfig(
                                mapping.source(),
                                downstream
                        );


                column.setTransformType(
                        "DIRECT"
                );


                return column;
            }


            /*
             * =================================
             * CONSTANT
             * =================================
             *
             * @Mapping(
             *     target = "productType",
             *     constant = "Plain Vanilla Swap"
             * )
             */
            if (!mapping
                    .constant()
                    .isBlank()) {


                ColumnLineageConfig column =
                        new ColumnLineageConfig(
                                "constant:"
                                        + mapping.constant(),
                                downstream
                        );


                column.setTransformType(
                        "CONSTANT"
                );


                return column;
            }


            /*
             * =================================
             * EXPRESSION
             * =================================
             *
             * @Mapping(
             *     target = "positionId",
             *     expression = "java(...)"
             * )
             */
            if (!mapping
                    .expression()
                    .isBlank()) {


                ColumnLineageConfig column =
                        new ColumnLineageConfig(
                                mapping.expression(),
                                downstream
                        );


                column.setTransformType(
                        "EXPRESSION"
                );


                return column;
            }


            /*
             * =================================
             * FALLBACK
             * =================================
             *
             * Kein source,
             * kein constant,
             * keine expression.
             *
             * Dann wird von einer
             * gleichnamigen Zuordnung ausgegangen.
             */
            ColumnLineageConfig column =
                    new ColumnLineageConfig(
                            downstream,
                            downstream
                    );


            column.setTransformType(
                    "DIRECT"
            );


            return column;
        }


        // =====================================================
        // Felder eines Java-Typs ermitteln
        // =====================================================

        private Set<String> fieldsOf(
                TypeMirror typeMirror) {


            if (!(typeMirror
                    instanceof DeclaredType declared)) {

                return Set.of();
            }


            TypeElement type =
                    (TypeElement)
                            declared
                                    .asElement();


            Set<String> fields =
                    new LinkedHashSet<>();


            for (Element element :
                    type.getEnclosedElements()) {


                /*
                 * Normale Java-Felder.
                 */
                if (element.getKind()
                        == ElementKind.FIELD) {


                    /*
                     * static Felder interessieren
                     * uns für die Lineage nicht.
                     */
                    if (!element
                            .getModifiers()
                            .contains(
                                    Modifier.STATIC)) {


                        fields.add(
                                element
                                        .getSimpleName()
                                        .toString()
                        );
                    }
                }


                /*
                 * Java Records.
                 */
                if (element.getKind()
                        == ElementKind.RECORD_COMPONENT) {


                    fields.add(
                            element
                                    .getSimpleName()
                                    .toString()
                    );
                }
            }


            return fields;
        }


        // =====================================================
        // Typname
        // =====================================================

        private String simpleTypeName(
                TypeMirror typeMirror) {


            if (typeMirror
                    instanceof DeclaredType declared) {


                return declared
                        .asElement()
                        .getSimpleName()
                        .toString();
            }


            return typeMirror.toString();
        }
    }
}