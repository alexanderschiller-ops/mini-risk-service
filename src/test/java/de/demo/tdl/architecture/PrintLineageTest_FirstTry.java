package de.demo.tdl.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

class PrintLineageTest_FirstTry {

    @Test
    void printLineageFromAllMapStructMappers() throws Exception {

        List<LineageEntry> lineage = new ArrayList<>();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(null, null, null)) {

            List<File> sourceFiles;

            try (var files = Files.walk(Path.of("src", "main", "java"))) {
                sourceFiles = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(Path::toFile)
                        .toList();
            }

            var compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(sourceFiles);

            var task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of(
                            "-proc:only",
                            "-classpath",
                            System.getProperty("java.class.path")
                    ),
                    null,
                    compilationUnits
            );

            task.setProcessors(
                    List.of(new LineageProcessor(lineage))
            );

            task.call();
        }

        assertFalse(
                lineage.isEmpty(),
                "Es wurde keine MapStruct-Lineage gefunden");

        lineage.forEach(entry ->
                System.out.println("entry.upstream(): "
                				+ entry.upstream()
                                + " -> "
                                + "entry.downstream(): "
                                + entry.downstream()
                                + " [" + entry.transformType() + "]"
                ));
    }


    // =========================================================
    // Annotation Processor
    // =========================================================

    @SupportedAnnotationTypes("org.mapstruct.Mapper")
    static class LineageProcessor extends AbstractProcessor {

        private final List<LineageEntry> result;

        LineageProcessor(List<LineageEntry> result) {
            this.result = result;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(
                Set<? extends TypeElement> annotations,
                RoundEnvironment roundEnv) {

            TypeElement mapperAnnotation =
                    processingEnv
                            .getElementUtils()
                            .getTypeElement(Mapper.class.getName());

            if (mapperAnnotation == null) {
                return false;
            }

            for (Element element :
                    roundEnv.getElementsAnnotatedWith(mapperAnnotation)) {

                /*
                 * Nur Typen mit @Mapper werden betrachtet.
                 */
                if (element.getKind() != ElementKind.INTERFACE
                        && element.getKind() != ElementKind.CLASS) {
                    continue;
                }

                TypeElement mapper = (TypeElement) element;

                analyzeMapper(mapper);
            }

            return false;
        }


        private void analyzeMapper(TypeElement mapper) {

            for (Element element : mapper.getEnclosedElements()) {

                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }

                ExecutableElement method =
                        (ExecutableElement) element;

                analyzeMappingMethod(method);
            }
        }


        private void analyzeMappingMethod(
                ExecutableElement method) {

            /*
             * 1. Implizite MapStruct-Mappings ermitteln:
             *
             * source.name -> target.name
             */
            Map<String, LineageEntry> mappings =
                    implicitMappings(method);

            /*
             * 2. Echte MapStruct @Mapping-Objekte lesen.
             *
             * KEIN JavaParser
             * KEIN pair.getNameAsString()
             * KEIN pair.getValue()
             */
            Mapping[] explicitMappings =
                    method.getAnnotationsByType(Mapping.class);

            for (Mapping mapping : explicitMappings) {

                String target = mapping.target();

                String source;
                String transformType;

                if (!mapping.source().isBlank()) {

                    source = mapping.source();
                    transformType = "DIRECT";

                } else if (!mapping.constant().isBlank()) {

                    source = "Konstante: "
                            + mapping.constant();

                    transformType = "CONSTANT";

                } else if (!mapping.expression().isBlank()) {

                    source = mapping.expression();
                    transformType = "TRANSFORM";

                } else if (!mapping.defaultExpression().isBlank()) {

                    source = mapping.defaultExpression();
                    transformType = "TRANSFORM";

                } else {

                    source = target;
                    transformType = "DIRECT";
                }

                /*
                 * Explizites @Mapping überschreibt
                 * implizites Mapping.
                 */
                mappings.put(
                        target,
                        new LineageEntry(
                                source,
                                target,
                                transformType
                        )
                );
            }

            result.addAll(mappings.values());
        }


        // =====================================================
        // Implizite Mappings
        // =====================================================

        private Map<String, LineageEntry> implicitMappings(
                ExecutableElement method) {

            Map<String, LineageEntry> lineage =
                    new LinkedHashMap<>();

            /*
             * Für das einfache MapStruct-Muster:
             *
             * Target map(Source source)
             */
            if (method.getParameters().size() != 1) {
                return lineage;
            }

            VariableElement sourceParameter =
                    method.getParameters().get(0);

            TypeMirror sourceType =
                    sourceParameter.asType();

            TypeMirror targetType =
                    method.getReturnType();

            Set<String> sourceFields =
                    fieldsOf(sourceType);

            Set<String> targetFields =
                    fieldsOf(targetType);

            for (String target : targetFields) {

                /*
                 * MapStruct mappt gleichnamige Felder
                 * automatisch.
                 */
                if (sourceFields.contains(target)) {

                    lineage.put(
                            target,
                            new LineageEntry(
                                    target,
                                    target,
                                    "DIRECT"
                            )
                    );
                }
            }

            return lineage;
        }


        private Set<String> fieldsOf(
                TypeMirror typeMirror) {

            if (!(typeMirror instanceof DeclaredType declaredType)) {
                return Set.of();
            }

            TypeElement type =
                    (TypeElement) declaredType.asElement();

            return type.getEnclosedElements()
                    .stream()
                    .filter(element ->
                            element.getKind() == ElementKind.FIELD
                                    || element.getKind()
                                    == ElementKind.RECORD_COMPONENT)
                    .map(Element::getSimpleName)
                    .map(Object::toString)
                    .filter(name ->
                            !name.equals("serialVersionUID"))
                    .collect(
                            java.util.stream.Collectors.toSet()
                    );
        }
    }


    // =========================================================
    // Zielmodell
    // =========================================================

    record LineageEntry(
            String upstream,
            String downstream,
            String transformType) {
    }
}