# Mini Risk Service – Excel-Testdaten, MapStruct und OpenLineage

Datenfluss: `Excel-Testdaten -> InputDB/CSV -> map_swap_cashflows -> risk_positions -> risk_positions_to_rm3d -> RM3D`.

## Testdaten aus MVP_Beispiel.xlsx

Unter `data/input/` liegen die aus dem Excel übernommenen 20 Halbjahresperioden:

- `swap_cashflows.csv`: Trade `00001`, Nominal 100.000 EUR, Fixzins 4 %, Payer/Receiver/Netto-Cashflows
- `forward_reference_rates.csv`: Forward-/Referenzzinswerte 2,0 % bis 4,2 % usw.
- `discount_rates.csv`: Discount Rates 1,9 % bis 3,8 %

Für die ausführbare Demo ist das Excel-Sheet `2_Marktdaten_Mapping` maßgeblich. So wird der dort gezeigte Marktwert reproduziert.

## Bewertungslogik

`Payer CF = -Nominal * FixedRate * 0.5`

`Receiver CF = Nominal * ForwardRate * 0.5`

`Netto CF = Payer CF + Receiver CF`

`DiscountFactor = 1 / (1 + DiscountRate * 0.5)^Periode`

`PresentValue = Netto CF * DiscountFactor`

Summe der 20 Barwerte: ca. `-6460.712349299317`, entsprechend dem Excel-Beispiel.

## YAML-Ausleitung

Die drei Contract-Templates liegen unter `src/main/resources/contracts/`. Beim Start kopiert `TdlContractExporter` sie in den gewünschten Projekt-Unterordner:

```text
tdl-output/
  map_swap_cashflows.yaml
  risk_positions_to_rm3d.yaml
  risk_positions.yaml
```

## OpenLineage + MapStruct

`RiskPositionMapper` mappt die angereicherten Cashflows auf `risk_positions`; `Rm3dMapper` mappt anschließend `trade_id` und `present_value` ins RM3D-Format. Der OpenLineage-Java-Client emittiert für beide Datajobs `START`, `COMPLETE` und im Fehlerfall `FAIL` inklusive Input-/Output-Datasets.

## Start

Voraussetzungen: JDK 21 und Maven 3.9+.

```bash
mvn clean test
mvn exec:java
```

Erzeugt werden `runtime-output/risk_positions.csv`, `travic-link/output/rm3d_output.rm3d` und die drei YAMLs unter `tdl-output/`.

Unter `example-output/` liegen bereits berechnete Referenzergebnisse zum Abgleich.
