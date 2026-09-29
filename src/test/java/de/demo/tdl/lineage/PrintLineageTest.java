package de.demo.tdl.lineage;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import de.demo.tdl.domain.RiskPosition;
import org.junit.jupiter.api.Test;
import org.mapstruct.Mapping;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;


//ToDo!!!
//Es darf keine Klasse in dem Test angegeben werden. Sondern das Mapper Interface muss Auskunft darüber geben!!!
//Es sollen in unserer Lineageausleitung nur Methoden aus dem Mapper Interface berücksichtigt werden wo @Mapper davor steht. Alle anderen sollen ignoriet werden.
//Das Interface soll alle auskünfte über die Lineage geben keine harten codiengs wie pair.getNameAsString(), oder pair.getValue().asStringLiteralExpr().asString()));
class PrintLineageTest {

    @Test
    void printLineage() throws Exception {
        Map<String, String> lineage = new LinkedHashMap<>();

        // MapStruct übernimmt gleichnamige Felder automatisch.
        for (var field : RiskPosition.class.getDeclaredFields()) {
            lineage.put(field.getName(), field.getName());
        }

        var javaCode = StaticJavaParser.parse(Path.of(
                "src/main/java/de/demo/tdl/mapper/RiskPositionMapper.java"));

        var method = javaCode.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals("toRiskPosition"))
                .findFirst()
                .orElseThrow();

        // Explizite @Mapping-Angaben überschreiben die Standardzuordnung.
        for (var annotation : method.getAnnotations()) {
            if (!annotation.getNameAsString().equals("Mapping")) {
                continue;
            }
            
            //@ChatGPT, damit habe ich ein Mapping-Objekt von MapStruct
            //Dieses Mapping-Objekt hat Felder die können gelesen werden.
            //########################################Mapping mapping = (Mapping) annotation;
            
            
            
            //Outputschema (YAML) soll eine Java Klasse sein.
            
            
            
            Map<String, String> values = new LinkedHashMap<>();
            annotation.asNormalAnnotationExpr().getPairs().forEach(pair ->
                    values.put(
                            pair.getNameAsString(),
                            pair.getValue().asStringLiteralExpr().asString()));

            String source = values.getOrDefault("source",
                    values.containsKey("constant")
                            ? "Konstante: " + values.get("constant")
                            : values.getOrDefault("expression", values.get("target")));

            lineage.put(values.get("target"), source);
        }

        lineage.forEach((target, source) ->
                System.out.println(source + " -> RiskPosition." + target));
    }
}