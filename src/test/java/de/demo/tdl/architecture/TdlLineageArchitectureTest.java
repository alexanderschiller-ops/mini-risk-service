package de.demo.tdl.architecture;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architektur-Test fuer Technical Data Lineage.
 *
 * <p>Einfache MapStruct-Mappings (gleicher Feldname, source/target, constant)
 * gelten als automatisch auswertbar. Bei Konstrukten, deren Semantik nicht
 * sicher aus MapStruct-Metadaten ableitbar ist, muss der Entwickler
 * {@code @TdlManual} bereitstellen. Fehlt die Annotation, schlaegt der Build fehl.</p>
 */
class TdlLineageArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    private static final Set<String> RISKY_MAPPING_ATTRIBUTES = Set.of(
            "expression",
            "defaultExpression",
            "conditionExpression",
            "qualifiedBy",
            "qualifiedByName",
            "conditionQualifiedBy",
            "conditionQualifiedByName"
    );

    private static final Set<String> RISKY_SELECTION_ATTRIBUTES = Set.of(
            "qualifiedBy",
            "qualifiedByName"
    );

    private static final Set<String> RISKY_METHOD_ANNOTATIONS = Set.of(
            "BeforeMapping",
            "AfterMapping",
            "ObjectFactory",
            "InheritConfiguration",
            "InheritInverseConfiguration",
            "SubclassMapping"
    );

    @Test
    void allMapStructMappingsMustHaveResolvableLineage() throws IOException {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

        List<String> problems = new ArrayList<>();
        List<Path> javaFiles;

        try (var files = Files.walk(SOURCE_ROOT)) {
            javaFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }

        for (Path path : javaFiles) {
            analyze(path, problems);
        }

        assertTrue(
                problems.isEmpty(),
                () -> "TDL-Architekturverletzungen:\n - " + String.join("\n - ", problems));
    }

    private static void analyze(Path path, List<String> problems) throws IOException {
        CompilationUnit unit = StaticJavaParser.parse(path);

        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            Optional<AnnotationExpr> mapperAnnotation = annotation(type, "Mapper");
            if (mapperAnnotation.isEmpty()) {
                continue;
            }

            validateManualAnnotations(path, type, problems);

            List<String> typeReasons = new ArrayList<>();
            if (hasAttribute(mapperAnnotation.get(), "uses")) {
                typeReasons.add("@Mapper(uses=...) kann benutzerdefinierte Konvertierungen implizit aufrufen");
            }
            if (hasAttribute(mapperAnnotation.get(), "config")) {
                typeReasons.add("@Mapper(config=...) verlagert Mapping-Regeln in eine externe Konfiguration");
            }
            if (hasAttribute(mapperAnnotation.get(), "mappingControl")) {
                typeReasons.add("@Mapper(mappingControl=...) veraendert die Mapping-Strategie");
            }
            if (hasAnnotation(type, "DecoratedWith")) {
                typeReasons.add("@DecoratedWith kann das generierte Mapping nachtraeglich veraendern");
            }

            if (!typeReasons.isEmpty() && !hasAnnotation(type, "TdlManual")) {
                problems.add(location(path, type) + ": " + String.join("; ", typeReasons)
                        + " -> @TdlManual auf dem Mapper erforderlich");
            }

            for (MethodDeclaration method : type.getMethods()) {
                validateManualAnnotations(path, method, problems);
                List<String> reasons = riskyReasons(method);
                if (!reasons.isEmpty() && !hasAnnotation(method, "TdlManual")) {
                    problems.add(location(path, method) + ": Mapper-Methode " + method.getNameAsString()
                            + " ist nicht sicher automatisch auswertbar: " + String.join("; ", reasons)
                            + " -> @TdlManual erforderlich");
                }
            }
        }
    }

    private static List<String> riskyReasons(MethodDeclaration method) {
        List<String> reasons = new ArrayList<>();

        if (method.getBody().isPresent() && !method.isPrivate()) {
            reasons.add("eigene Java-Implementierung/Default-Methode");
        }

        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = simpleName(annotation);

            if (name.equals("Mapping")) {
                for (String attribute : RISKY_MAPPING_ATTRIBUTES) {
                    if (hasAttribute(annotation, attribute)) {
                        reasons.add("@Mapping(" + attribute + "=...)");
                    }
                }
            }

            if (Set.of("BeanMapping", "IterableMapping", "MapMapping").contains(name)) {
                for (String attribute : RISKY_SELECTION_ATTRIBUTES) {
                    if (hasAttribute(annotation, attribute)) {
                        reasons.add("@" + name + "(" + attribute + "=...)");
                    }
                }
            }

            if (RISKY_METHOD_ANNOTATIONS.contains(name)) {
                reasons.add("@" + name);
            }
        }

        long sourceParameters = method.getParameters().stream()
                .filter(parameter -> !hasAnnotation(parameter, "MappingTarget"))
                .filter(parameter -> !hasAnnotation(parameter, "Context"))
                .filter(parameter -> !hasAnnotation(parameter, "TargetType"))
                .count();

        if (sourceParameters > 1) {
            reasons.add("mehrere Source-Parameter koennen implizite Feldherkunft mehrdeutig machen");
        }

        for (Parameter parameter : method.getParameters()) {
            if (hasAnnotation(parameter, "MappingTarget")) {
                reasons.add("@MappingTarget aktualisiert ein bestehendes Zielobjekt");
            }
            if (hasAnnotation(parameter, "Context")) {
                reasons.add("@Context kann Mapping-Ergebnisse ueber benutzerdefinierte Logik beeinflussen");
            }
        }

        return reasons.stream().distinct().toList();
    }

    private static void validateManualAnnotations(
            Path path,
            NodeWithAnnotations<?> node,
            List<String> problems) {

        for (AnnotationExpr annotation : node.getAnnotations()) {
            if (!simpleName(annotation).equals("TdlManual")) {
                continue;
            }

            if (!annotation.isNormalAnnotationExpr()) {
                problems.add(location(path, (Node) node)
                        + ": @TdlManual muss source, target und type explizit angeben");
                continue;
            }

            Set<String> attributes = annotation.asNormalAnnotationExpr().getPairs().stream()
                    .map(MemberValuePair::getNameAsString)
                    .collect(java.util.stream.Collectors.toSet());

            for (String required : Set.of("source", "target", "type")) {
                if (!attributes.contains(required)) {
                    problems.add(location(path, (Node) node)
                            + ": @TdlManual Pflichtattribut fehlt: " + required);
                }
            }

            annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("source")
                            || pair.getNameAsString().equals("target"))
                    .filter(pair -> pair.getValue().isStringLiteralExpr())
                    .filter(pair -> pair.getValue().asStringLiteralExpr().asString().isBlank())
                    .forEach(pair -> problems.add(location(path, (Node) node)
                            + ": @TdlManual " + pair.getNameAsString() + " darf nicht leer sein"));
        }
    }

    private static Optional<AnnotationExpr> annotation(
            NodeWithAnnotations<?> node,
            String simpleName) {

        return node.getAnnotations().stream()
                .filter(candidate -> simpleName(candidate).equals(simpleName))
                .findFirst();
    }

    private static boolean hasAnnotation(NodeWithAnnotations<?> node, String simpleName) {
        return annotation(node, simpleName).isPresent();
    }

    private static boolean hasAttribute(AnnotationExpr annotation, String attribute) {
        return annotation.isNormalAnnotationExpr()
                && annotation.asNormalAnnotationExpr().getPairs().stream()
                .anyMatch(pair -> pair.getNameAsString().equals(attribute));
    }

    private static String simpleName(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static String location(Path path, Node node) {
        int line = node.getBegin().map(position -> position.line).orElse(-1);
        return path + (line > 0 ? ":" + line : "");
    }
}
