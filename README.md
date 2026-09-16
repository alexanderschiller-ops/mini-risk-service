# mini-risk-service

Java-21-Draft fuer Technical Data Lineage eines Swap-Risikoprozesses mit MapStruct und OpenLineage.

## Ablauf

`swap_cashflows + forward_rates + discount_rates -> map_swap_cashflows -> risk_positions -> risk_positions_to_rm3d -> rm3d_output`

Die Beispieldaten aus dem Excel liegen unter `data/input/`. Die Anwendung erzeugt die RisikoPosDB-Simulation unter `runtime-output/risk_positions.csv`, die RM3D-Ausgabe unter `travic-link/output/rm3d_output.rm3d` und exportiert die TDL-Vertraege nach `tdl-output/`.

## Kommentargesteuerter TDL-Agent

Die technische Lineage kann aus strukturierten Java-Kommentaren erzeugt werden. Beispiele:

```java
// @tdl.job id=map_swap_cashflows name="Swap Cashflow Processing"
// @tdl.input dataset=swap_cashflows
// @tdl.output dataset=risk_positions
```

Feld-Lineage wird genauso beschrieben:

```java
// @tdl.field-map job=risk_positions_to_rm3d source=risk_positions.present_value target=rm3d.market_value
```

Der Generator liegt unter `tools/tdl_comment_agent.py`. Lokal kann er so ausgefuehrt werden:

```bash
python3 tools/tdl_comment_agent.py --source src/main/java --output tdl-output --contracts src/main/resources/contracts
```

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
