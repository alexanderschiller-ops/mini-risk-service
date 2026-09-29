package de.demo.tdl.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import de.demo.tdl.lineage.PipelineLineageV3;
import de.demo.tdl.lineage.PipelineLineageV3.ColumnLineageConfig;
import de.demo.tdl.lineage.PipelineLineageV3.DatasetRefConfig;
import de.demo.tdl.lineage.PipelineLineageV3.JobConfig;
import de.demo.tdl.lineage.PipelineLineageV3.InstitutionalMemoryConfig;
import de.demo.tdl.lineage.PipelineLineageV3.InstitutionalMemoryElement;
import de.demo.tdl.lineage.PipelineLineageV3.PipelineConfig;
import de.demo.tdl.lineage.PipelineLineageV3.TransformType;

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
import java.net.URI;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Node;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintLineageTestV3 {

    @Test
    void printLineageYaml() throws Exception {

        List<PipelineLineageV3> result =
                new ArrayList<>();

        Path pomFile =
                Path.of("pom.xml");

        String application =
                mavenApplicationUrn(
                        pomFile);

        String documentationTitle =
                mavenDocumentationTitle(
                        pomFile);

        URI documentationUrl =
                mavenProjectUrl(
                        pomFile);

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
                                    result,
                                    application,
                                    documentationTitle,
                                    documentationUrl)));

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
        for (PipelineLineageV3 lineage : result) {

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



    private static final Pattern MAVEN_PROPERTY =
            Pattern.compile("\\$\\{([^}]+)}");

    private static String mavenApplicationUrn(
            Path pomFile) throws Exception {

        if (!Files.isRegularFile(pomFile)) {
            throw new IllegalStateException(
                    "pom.xml nicht gefunden: "
                            + pomFile.toAbsolutePath());
        }

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(false);

        org.w3c.dom.Document document =
                factory
                        .newDocumentBuilder()
                        .parse(pomFile.toFile());

        org.w3c.dom.Element project =
                document.getDocumentElement();

        org.w3c.dom.Element parent =
                directChild(
                        project,
                        "parent");

        String groupId =
                firstNonBlank(
                        directChildText(
                                project,
                                "groupId"),
                        directChildText(
                                parent,
                                "groupId"));

        String artifactId =
                directChildText(
                        project,
                        "artifactId");

        String version =
                firstNonBlank(
                        directChildText(
                                project,
                                "version"),
                        directChildText(
                                parent,
                                "version"));

        Map<String, String> properties =
                mavenProperties(project);

        properties.put(
                "project.groupId",
                groupId);
        properties.put(
                "pom.groupId",
                groupId);
        properties.put(
                "project.artifactId",
                artifactId);
        properties.put(
                "pom.artifactId",
                artifactId);
        properties.put(
                "project.version",
                version);
        properties.put(
                "pom.version",
                version);

        groupId =
                resolveMavenProperties(
                        groupId,
                        properties);

        artifactId =
                resolveMavenProperties(
                        artifactId,
                        properties);

        version =
                resolveMavenProperties(
                        version,
                        properties);

        if (groupId == null
                || groupId.isBlank()
                || groupId.contains("${")
                || artifactId == null
                || artifactId.isBlank()
                || artifactId.contains("${")
                || version == null
                || version.isBlank()
                || version.contains("${")) {

            throw new IllegalStateException(
                    "Maven-Koordinaten konnten nicht vollständig "
                            + "aus pom.xml gelesen bzw. aufgelöst werden");
        }

        return "urn:"
                + groupId
                + ":"
                + artifactId
                + ":"
                + version;
    }


    private static URI mavenProjectUrl(
            Path pomFile) throws Exception {

        org.w3c.dom.Element project =
                readMavenProject(
                        pomFile);

        Map<String, String> properties =
                mavenPropertiesWithCoordinates(
                        project);

        String url =
                resolveMavenProperties(
                        directChildText(
                                project,
                                "url"),
                        properties);

        if (url == null
                || url.isBlank()
                || url.contains("${")) {

            throw new IllegalStateException(
                    "Maven <project><url> fehlt oder konnte "
                            + "nicht vollständig aufgelöst werden");
        }

        URI uri =
                new URI(
                        url.trim());

        if (!uri.isAbsolute()) {
            throw new IllegalStateException(
                    "Maven <project><url> muss eine absolute URI sein: "
                            + url);
        }

        return uri;
    }


    private static String mavenDocumentationTitle(
            Path pomFile) throws Exception {

        org.w3c.dom.Element project =
                readMavenProject(
                        pomFile);

        Map<String, String> properties =
                mavenPropertiesWithCoordinates(
                        project);

        String name =
                resolveMavenProperties(
                        directChildText(
                                project,
                                "name"),
                        properties);

        if (name != null
                && !name.isBlank()
                && !name.contains("${")) {

            return name;
        }

        String artifactId =
                resolveMavenProperties(
                        directChildText(
                                project,
                                "artifactId"),
                        properties);

        if (artifactId == null
                || artifactId.isBlank()
                || artifactId.contains("${")) {

            throw new IllegalStateException(
                    "Weder Maven <name> noch <artifactId> "
                            + "konnten als Dokumentationstitel ermittelt werden");
        }

        return artifactId;
    }


    private static org.w3c.dom.Element readMavenProject(
            Path pomFile) throws Exception {

        if (!Files.isRegularFile(pomFile)) {
            throw new IllegalStateException(
                    "pom.xml nicht gefunden: "
                            + pomFile.toAbsolutePath());
        }

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(false);

        org.w3c.dom.Document document =
                factory
                        .newDocumentBuilder()
                        .parse(pomFile.toFile());

        return document.getDocumentElement();
    }


    private static Map<String, String> mavenPropertiesWithCoordinates(
            org.w3c.dom.Element project) {

        org.w3c.dom.Element parent =
                directChild(
                        project,
                        "parent");

        String groupId =
                firstNonBlank(
                        directChildText(
                                project,
                                "groupId"),
                        directChildText(
                                parent,
                                "groupId"));

        String artifactId =
                directChildText(
                        project,
                        "artifactId");

        String version =
                firstNonBlank(
                        directChildText(
                                project,
                                "version"),
                        directChildText(
                                parent,
                                "version"));

        Map<String, String> properties =
                mavenProperties(
                        project);

        if (groupId != null) {
            properties.put("project.groupId", groupId);
            properties.put("pom.groupId", groupId);
        }

        if (artifactId != null) {
            properties.put("project.artifactId", artifactId);
            properties.put("pom.artifactId", artifactId);
        }

        if (version != null) {
            properties.put("project.version", version);
            properties.put("pom.version", version);
        }

        return properties;
    }


    private static Map<String, String> mavenProperties(
            org.w3c.dom.Element project) {

        Map<String, String> result =
                new HashMap<>();

        org.w3c.dom.Element properties =
                directChild(
                        project,
                        "properties");

        if (properties == null) {
            return result;
        }

        for (Node node =
                     properties.getFirstChild();
             node != null;
             node = node.getNextSibling()) {

            if (node.getNodeType()
                    == Node.ELEMENT_NODE) {

                result.put(
                        node.getNodeName(),
                        node.getTextContent().trim());
            }
        }

        return result;
    }


    private static String resolveMavenProperties(
            String value,
            Map<String, String> properties) {

        if (value == null) {
            return null;
        }

        String resolved = value;

        for (int pass = 0; pass < 10; pass++) {

            Matcher matcher =
                    MAVEN_PROPERTY.matcher(
                            resolved);

            StringBuffer buffer =
                    new StringBuffer();

            boolean changed = false;

            while (matcher.find()) {

                String replacement =
                        properties.get(
                                matcher.group(1));

                if (replacement == null) {
                    continue;
                }

                matcher.appendReplacement(
                        buffer,
                        Matcher.quoteReplacement(
                                replacement));

                changed = true;
            }

            matcher.appendTail(
                    buffer);

            if (!changed) {
                break;
            }

            resolved =
                    buffer.toString();
        }

        return resolved;
    }


    private static String firstNonBlank(
            String first,
            String second) {

        if (first != null
                && !first.isBlank()) {

            return first;
        }

        return second;
    }


    private static String directChildText(
            org.w3c.dom.Element parent,
            String childName) {

        org.w3c.dom.Element child =
                directChild(
                        parent,
                        childName);

        return child == null
                ? null
                : child
                        .getTextContent()
                        .trim();
    }


    private static org.w3c.dom.Element directChild(
            org.w3c.dom.Element parent,
            String childName) {

        if (parent == null) {
            return null;
        }

        for (Node node =
                     parent.getFirstChild();
             node != null;
             node = node.getNextSibling()) {

            if (node.getNodeType()
                    == Node.ELEMENT_NODE
                    && childName.equals(
                    node.getNodeName())) {

                return (org.w3c.dom.Element) node;
            }
        }

        return null;
    }


    // =========================================================
    // Annotation Processor
    // =========================================================

    static class LineageProcessor
            extends AbstractProcessor {

        private final List<PipelineLineageV3> result;
        private final String application;
        private final String documentationTitle;
        private final URI documentationUrl;


        LineageProcessor(
                List<PipelineLineageV3> result,
                String application,
                String documentationTitle,
                URI documentationUrl) {

            this.result = result;
            this.application = application;
            this.documentationTitle = documentationTitle;
            this.documentationUrl = documentationUrl;
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

        private PipelineLineageV3 createPipeline(
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

            pipeline.setApplication(
                    application);

            pipeline.setInstitutionalMemory(
                    createInstitutionalMemory());


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
            return new PipelineLineageV3(
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

            job.setApplication(
                    application);

            job.setInstitutionalMemory(
                    createInstitutionalMemory());


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
                                    List.of(target),
                                    List.of(target)
                            );


                    column.setTransformType(
                            TransformType.DIRECT
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
                                List.of(mapping.source()),
                                List.of(downstream)
                        );


                column.setTransformType(
                        TransformType.DIRECT
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
                                List.of(
                                        "constant:"
                                                + mapping.constant()),
                                List.of(downstream)
                        );


                column.setTransformType(
                        TransformType.CONSTANT
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
                                List.of(mapping.expression()),
                                List.of(downstream)
                        );


                column.setTransformType(
                        TransformType.EXPRESSION
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
                            List.of(downstream),
                            List.of(downstream)
                    );


            column.setTransformType(
                    TransformType.DIRECT
            );


            return column;
        }


        private InstitutionalMemoryConfig createInstitutionalMemory() {

            InstitutionalMemoryElement element =
                    new InstitutionalMemoryElement(
                            documentationTitle,
                            documentationUrl);

            InstitutionalMemoryConfig memory =
                    new InstitutionalMemoryConfig();

            memory.setElements(
                    List.of(element));

            return memory;
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