# mini-risk-service

Java-21-Beispiel für technische Data Lineage eines Swap-Risikoprozesses mit PostgreSQL, MapStruct und OpenLineage.

## Datenfluss

`PostgreSQL: swap_cashflows + forward_rates + discount_rates -> map_swap_cashflows -> risk_positions.csv -> risk_positions_to_rm3d -> rm3d_output.rm3d`

`InputDb` liest ausschließlich über JDBC aus PostgreSQL. `RiskPositionDb` persistiert weiterhin eine CSV-Datei; die RM3D-Ausgabe ist ebenfalls eine Datei. Beide Ausgaben werden deshalb als Datei-Datasets ausgewiesen. Fachliche Berechnung und MapStruct-Zuordnungen bleiben unverändert.

## PostgreSQL starten und Anwendung ausführen

Voraussetzungen: JDK 21+, Maven 3.9+ und Docker mit laufender Engine, alternativ eine bestehende PostgreSQL-Datenbank.

```shell
docker compose up -d --wait
mvn clean test
mvn exec:java
```

Compose startet PostgreSQL 17 auf `127.0.0.1:5432`, Datenbank `inputdb`, Benutzer/Passwort jeweils `inputdb` für die lokale Demo. Ein benanntes Volume hält die Daten. Beim ersten Start mit leerem Volume werden `db/01-schema.sql` und `db/02-seed.sql` ausgeführt. Die SQL-Dateien erstellen drei Tabellen in `public` und laden die vorhandenen Beispieldaten aus `data/input/` als SQL-Seed. Der Anwendungscode liest diese CSV-Dateien nicht mehr.

Die Initialisierung ersetzt keine vorhandenen Tabellen oder Datensätze: Der Seed verwendet `ON CONFLICT DO NOTHING`. Änderungen an Init-Dateien werden in einem bereits initialisierten Compose-Volume nicht automatisch ausgeführt.

Eine andere PostgreSQL-Instanz wird über Umgebungsvariablen konfiguriert, beispielsweise in PowerShell:

```powershell
$env:INPUT_DB_URL = 'jdbc:postgresql://localhost:5432/inputdb'
$env:INPUT_DB_USER = 'inputdb'
$env:INPUT_DB_PASSWORD = 'inputdb'
mvn exec:java
```

Benötigt werden die Tabellen `public.swap_cashflows`, `public.forward_rates`, `public.discount_rates`; die Anwendung selbst erstellt oder befüllt keine Tabellen. `numeric` und Java `BigDecimal` bewahren die Genauigkeit der Beispieldaten. `INPUT_DB_PORT` ändert den veröffentlichten Compose-Port; bei einem anderen Port muss auch `INPUT_DB_URL` entsprechend gesetzt werden.

Die Anwendung schreibt:

- `runtime-output/risk_positions.csv` – 20 Risikopositionen aus den Demo-Eingaben
- `travic-link/output/rm3d_output.rm3d` – RM3D-Ausgabe
- OpenLineage-Laufzeitereignisse auf die Konsole

Der erwartete Gesamtmarktwert des Demo-Swaps ist rund `-6460.712349299317`.

## TDL-Ausleitung als YAML

Maßgeblich sind die bereitgestellten JSON-Schemata für Dataset-YAML und Pipeline-Lineage. Die Excel-Dokumentation ist eine ergänzende Beschreibung. Die Ausleitung erzeugt YAML, keine Excel-Dateien.

```shell
mvn -Dtest=TdlContractExportTest test
```

In Eclipse: `TdlContractExportTest` unter `src/test/java` als JUnit-Test starten, danach das Projekt mit F5 aktualisieren. Der Export benötigt keine laufende Datenbank. Die `main`-Methode führt die fachliche Verarbeitung aus; die YAML-Ausleitung bleibt im Testlauf.

Die Exportdateien liegen getrennt unter:

```text
target/tdl-output/
  datasets/
    swap_cashflows.yaml
    forward_rates.yaml
    discount_rates.yaml
    risk_positions.yaml
    rm3d_output.yaml
  datajobs/
    mini-risk-pipeline.yaml
```

Ein **Dataset** beschreibt einen gespeicherten Datenbestand mit `platform`, `name`, `env` und `schema`. Datenbanktabellen erhalten Namen wie `inputdb.public.swap_cashflows` und die Plattform `postgres`. Die beiden bestehenden Dateiausgaben verwenden die Plattform `file` und ihren relativen Dateipfad als Namen.

Ein **Datajob** beschreibt eine Transformation unter `pipeline.jobs`, einschließlich `inputs`, `outputs` und `column_lineage`. Die Pipeline enthält zwei Jobs: `map_swap_cashflows` und `risk_positions_to_rm3d`. Die Referenzen stimmen in Name, Plattform und Umgebung mit den Dataset-Dateien überein.

Die Typen `TdlDataset` und `TdlLineage` bilden diese zwei Formate getrennt ab. `TdlContractExporter` erzeugt sie mit Jackson YAML. Die Spalten- und Transformationsmetadaten werden dort ausdrücklich gepflegt; es wird keine automatische Analyse beliebigen Java-Codes behauptet. Bei fachlichen Änderungen müssen die Metadaten mit angepasst werden. Unter `tdl-output/` liegen generierte Beispielverträge für die Standardkonfiguration.

Die JSON-Schemata legen die interne Syntax der Strings in `column_lineage` nicht fest. Der Export verwendet ausdrücklich die Konvention `alias.spalte`, zum Beispiel `cashflows.nominal`; `alias` stammt aus den jeweiligen Ein-/Ausgabereferenzen. Der konsumierende YAML-Importer muss diese Konvention unterstützen. Eine Konstante wie `product_type` hat `upstream: []` und eine Beschreibung mit dem konstanten Wert.

`TdlExportConfig` erlaubt abweichende Dataset-Namen und Umgebungen. Die Standardumgebung ist `DEV`. Die privaten Referenzdateien werden nicht im öffentlichen Repository abgelegt.

## Tests

`mvn test` prüft YAML-Inhalte und Referenzen, Datenbankkonfiguration und fachliche Feldzuordnungen. Die früheren Architekturtests und ihre eigenen Markierungsannotationstypen sind entfernt. Die normalen MapStruct-Annotationen bleiben für die Mapper-Generierung erhalten.

Die drei PostgreSQL-Integrationstests laufen nur bei gesetzter `INPUT_DB_TEST_URL`; ohne diese Variable werden sie sichtbar übersprungen. Für die lokale Compose-Demo:

```powershell
$env:INPUT_DB_TEST_URL = 'jdbc:postgresql://localhost:5432/inputdb'
$env:INPUT_DB_TEST_USER = 'inputdb'
$env:INPUT_DB_TEST_PASSWORD = 'inputdb'
mvn verify
```

Die Testdatenbank muss mit den beiden SQL-Dateien initialisiert sein. Die Tests lesen Daten und prüfen unter anderem führende Nullen in IDs, Datumswerte, Dezimalgenauigkeit und die vollständige Pipeline mit 20 Positionen. Sie verändern die PostgreSQL-Eingaben nicht. Verwende dafür die Demo-Testdatenbank.

Der GitHub-Workflow startet einen isolierten PostgreSQL-Service, initialisiert das Schema und die Testdaten und führt `mvn verify` mit aktivierten Integrationstests aus. Anschließend stellt er die YAML-Ausleitung als Build-Artefakt bereit.
