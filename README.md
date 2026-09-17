# mini-risk-service

Java-21-Draft fuer Technical Data Lineage eines Swap-Risikoprozesses mit MapStruct und OpenLineage.

## Ablauf

`swap_cashflows + forward_rates + discount_rates -> map_swap_cashflows -> risk_positions -> risk_positions_to_rm3d -> rm3d_output`

Die Beispieldaten aus dem Excel liegen unter `data/input/`. Die Anwendung erzeugt die RisikoPosDB-Simulation unter `runtime-output/risk_positions.csv` und die RM3D-Ausgabe unter `travic-link/output/rm3d_output.rm3d`. Die TDL-Vertraege werden beim Testlauf nach `target/tdl-output/` exportiert.

## TDL-Vertraege

Die TDL-YAMLs werden unter `src/main/resources/contracts/` gepflegt. `TdlContractExportTest` ruft `TdlContractExporter` auf, prueft Vollstaendigkeit und Inhalt der frisch exportierten Dateien und legt die geprueften Vertraege unter `target/tdl-output/` ab. Die `main`-Methode exportiert keine TDL-YAMLs mehr. Die vorhandenen `@tdl.*`-Kommentare dokumentieren die Verarbeitung; eine automatische YAML-Generierung aus diesen Kommentaren findet nicht statt.

Der Export laeuft mit `mvn test`. Nur die Ausleitung ausfuehren:

```bash
mvn -Dtest=TdlContractExportTest test
```

In Eclipse: Rechtsklick auf `TdlContractExportTest` unter `src/test/java` -> **Run As -> JUnit Test**. Danach das Projekt mit **F5** aktualisieren, um die Dateien unter `target/tdl-output/` zu sehen. Die bisherigen Beispieldateien unter `tdl-output/` werden dabei nicht aktualisiert. Laufzeit-OpenLineage-Events bleiben davon unabhaengig.

## MapStruct-Architektur-Test

Zusaetzlich prueft `TdlLineageArchitectureTest` alle MapStruct-Mapper. Einfache Same-Name-, `source/target`- und `constant`-Mappings gelten als automatisch auswertbar. Fortgeschrittene Konstrukte wie `expression`, `qualifiedByName`, Mapping-Lifecycle-Hooks, Decorators, externe `uses`-Mapper, mehrere Source-Parameter, `@Context` oder `@MappingTarget` muessen explizit mit `@TdlManual` dokumentiert werden.

Beispiel:

```java
@TdlManual(
    source = "tradeId,period",
    target = "positionId",
    type = TdlType.TRANSFORM,
    commentary = "positionId wird aus tradeId und period erzeugt")
@Mapping(target = "positionId", expression = "java(...)")
RiskPosition toRiskPosition(EnrichedSwapCashflow source);
```

Fehlt `@TdlManual` bei einem nicht sicher automatisch analysierbaren Mapping, schlaegt `mvn test` fehl und damit auch der GitHub-Workflow. Das ist ein Architektur-Test fuer das nicht-fachliche Qualitaetsziel Wartbarkeit.

## MapStruct

MapStruct mappt Properties mit gleichem Namen automatisch. Deshalb stehen im `RiskPositionMapper` nur fachlich notwendige explizite Mappings. Die Architektur-Regel zwingt nur bei nicht sicher ableitbaren Konstrukten zu manuellen Lineage-Metadaten.

## Build und Start

```bash
mvn clean test
mvn exec:java
```

MapStruct generiert die Implementierungen beim Compile unter `target/generated-sources/annotations`; die kompilierten Klassen liegen unter `target/classes`.

## Excel-Testdaten

Die Bewertung verwendet die Werte aus `2_Marktdaten_Mapping` als fachlich massgebliche Referenz. Der erwartete Gesamtmarktwert des Beispiel-Swaps ist rund `-6460.712349299317` bei 20 Risikopositionen.
