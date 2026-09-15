# mini-risk-service

Java-21-Draft fuer Technical Data Lineage eines Swap-Risikoprozesses mit MapStruct und OpenLineage.

## Ablauf

`swap_cashflows + forward_rates + discount_rates -> map_swap_cashflows -> risk_positions -> risk_positions_to_rm3d -> rm3d_output`

Die Beispieldaten aus dem Excel liegen unter `data/input/`. Die Anwendung erzeugt die RisikoPosDB-Simulation unter `runtime-output/risk_positions.csv`, die RM3D-Ausgabe unter `travic-link/output/rm3d_output.rm3d` und exportiert die drei TDL-Vertraege nach `tdl-output/`.

## MapStruct und Lineage

MapStruct mappt Properties mit gleichem Namen automatisch. Deshalb werden im `RiskPositionMapper` nur fachlich notwendige explizite Mappings angegeben, z. B. `forwardRate -> rate`, `discountRate -> discount`, eine Konstante oder eine Expression. Gleichnamige Felder wie `tradeId`, `cashflowDate`, `nominal` und `presentValue` brauchen keine redundante `@Mapping`-Annotation.

Wichtig: Die YAML-Vertraege werden nicht aus den `@Mapping`-Annotationen erzeugt. `TdlContractExporter` exportiert die statischen Contracts aus `src/main/resources/contracts`. Damit bleibt die Dataset-/Job-Lineage unabhaengig davon, ob MapStruct ein Feld explizit oder implizit mappt.

Fuer eine spaetere automatische Column-Lineage sollte die Extraktion beide Faelle beruecksichtigen: explizite `@Mapping`-Definitionen und MapStructs implizite Same-Name-Mappings. Entwickler sollen nicht gezwungen sein, redundante Annotationen nur fuer die Lineage zu pflegen.

## Build und Start

```bash
mvn clean test
mvn exec:java
```

MapStruct generiert die Implementierungen beim Compile unter `target/generated-sources/annotations`; die kompilierten Klassen liegen unter `target/classes`.

## Excel-Testdaten

Die Bewertung verwendet die Werte aus `2_Marktdaten_Mapping` als fachlich massgebliche Referenz. Die Sequenzen in den Raw-Curve-Sheets sind gegenueber den dortigen Labels auffaellig vertauscht. Der erwartete Gesamtmarktwert des Beispiel-Swaps ist rund `-6460.712349299317` bei 20 Risikopositionen.
