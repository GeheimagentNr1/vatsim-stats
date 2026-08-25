# VATSIM Statistik-Tool — Projektkontext

Dieses Dokument fasst die Architektur- und Technologieentscheidungen zusammen,
die für dieses Projekt getroffen wurden. Ziel: ein VATSIM-Statistik-Tool im
Stil von statsim.net.

## Kernfunktionen

1. **Heatmaps für Events** — zeigt, wo Flieger während eines Events entlanggeflogen sind.
2. **Session-Tracking** — alle ATC- und Piloten-Sessions werden getrackt und
   dauerhaft gespeichert. Abfragbar nach:
   - VATSIM-ID
   - Position-ID / Callsign
   - Zeitraum
   Darstellung: tabellarisch, als Heatmap (Verkehrsaufkommen nach Tag/Stunde)
   und als Graph (Zeitreihe).
3. **Replay-Feature** (noch nicht final entschieden, ob nötig) — nachgeflogene
   Flüge auf einer Karte mit Zeitsteuerung (Play/Pause/Speed).

## Entscheidung: Tech-Stack

**Ausgangslage:** Entwickler mit Java-Hintergrund, Erfahrung mit Vaadin und
Vue, außerdem Erfahrung mit Symfony/Twig/PHP. Offenheit für neue Technologien
im Rahmen des Projekts.

### Backend: Spring Boot (Java)

- REST-API für Abfragen (Sessions nach ID/Callsign/Zeitraum)
- Spring WebSocket/SSE für Live-Daten und ggf. Replay-Streaming
- Spring Data JPA/JDBC
- **PostgreSQL mit TimescaleDB-Extension** für Zeitreihendaten (Sessions,
  Positionsverläufe)
- Optional **PostGIS**, falls geografische Abfragen für Replay-Tracks nötig werden

### Frontend: Vue 3 (+ optional Nuxt für SSR/SEO bei öffentlichen Statistikseiten)

- Framework-Wahl fiel auf Vue statt React/Next.js, da bereits Vue-Erfahrung
  vorhanden ist — kein Mehrwert durch Frameworkwechsel für dieses Projekt.

**Karte / Heatmap / Replay:**
- **MapLibre GL JS** (Open Source, Mapbox-GL-Fork) — native Heatmap-Layer,
  performant bei vielen Punkten/Trajektorien
- **deck.gl** (ergänzend, kombinierbar mit MapLibre) — insbesondere
  **TripsLayer** für das Replay-Feature (Bewegungspfade mit Zeitverlauf,
  "Schweif"-Effekt)
- Einbindung in Vue z. B. über `vue-maplibre-gl` oder direkt via `onMounted`

**Charts (Verkehrsaufkommen nach Tag/Stunde, Zeitreihen):**
- **Apache ECharts** über `vue-echarts` — hat eingebaute Calendar-Heatmap-
  und Heatmap-Chart-Typen, passend für "Verkehr nach Stunde/Tag"

**Tabellen (Session-Abfragen):**
- **@tanstack/vue-table** — wichtig für serverseitiges Paging/Sortieren/
  Filtern bei potenziell sehr großen Datenmengen

### Verworfene / geprüfte Alternativen

- **Vaadin (Community-Edition) als alleiniges Frontend-Framework:** Grid-
  Komponente ist stark (lazy loading, serverseitiges Filtern/Sortieren) und
  wäre für die Session-Tabellen gut geeignet. Aber: Vaadin Charts ist
  kostenpflichtiges Add-on, und es gibt **keine native Map-Komponente**.
  Der aufwendigste Teil des Projekts (Karte, Heatmap, Replay) würde ohnehin
  Custom-JS-Wrapping erfordern — dafür lohnt sich der Umstieg auf Vaadin als
  Gesamtframework nicht. **Entscheidung: nicht als Hauptframework, aber als
  Lernprojekt separat interessant (siehe unten).**
- **Vaadin Hilla** (Spring-Boot-Backend + React/Lit-Frontend mit
  typsicherem generiertem Client) als möglicher Mittelweg identifiziert,
  aber verworfen zugunsten von Spring Boot + Vue, da dort sofort
  Produktivität durch vorhandene Vue-Erfahrung gegeben ist.
- **Symfony/Twig/PHP:** technisch möglich (JS-Bibliotheken sind backend-
  agnostisch), aber kein Vorteil gegenüber Spring Boot, da ohnehin ein
  separates JS-Frontend nötig wäre. Twig als Server-Side-Templating passt
  nicht zu einer interaktiven Karten-/Replay-Anwendung mit Client-State.
  **Entscheidung: nicht verwendet.**

## Implementierungsstand: Datenaufzeichnung (Ingestion & Phasenerkennung)

Erstes Teilprojekt (vor der eigentlichen UI) ist umgesetzt: zuverlässige,
dauerhafte Aufzeichnung von VATSIM-Piloten-/ATC-Sessions inkl. automatischer
Erkennung von Start-/Lande-/Touch-and-Go-Ereignissen. Vollständiges Design:
`docs/superpowers/specs/2026-08-24-data-ingestion-design.md`.

### Projektstruktur (Maven-Multi-Modul, Java 21)

- **`reference-data`** — täglicher Scheduled-Job (`OurAirportsScheduledImportJob`)
  lädt `airports.csv`/`runways.csv` von ourairports.com und upsertet sie in
  `airport`/`runway` (`OurAirportsImportService`, `OurAirportsCsvParser`).
  `AirportRepository`/`RunwayRepository` (Spring Data JPA).
- **`detection`** — reines Java, **keine Spring-Abhängigkeit**, unabhängig
  testbar: `PilotPhaseStateMachine` (Zustandsmaschine `ON_GROUND` /
  `AIRBORNE` / `GROUND_PENDING`, leitet `TAKEOFF`/`LANDING`/
  `TOUCH_AND_GO`/`LOW_APPROACH`-Events aus einer Folge von `TrackSample`s
  ab), `NearestAirportLookup` (Haversine-basierte Nächster-Nachbar-Suche,
  ~5nm-Bounding-Box-Vorfilter), `PhaseDetectionConfig`.
- **`ingestion`** — `IngestionPoller` (`@Scheduled`, alle 15s, ruft
  `VatsimDataFeedClient` gegen `https://data.vatsim.net/v3/vatsim-data.json`
  auf), `PilotSessionOrchestrator` (verdrahtet Rohdaten-Persistenz +
  `PilotPhaseStateMachine` + Session-/Event-Ableitung), `AtcSessionTracker`
  (einfachere Login/Logout-Session-Logik für ATC, kein Phasenmodell nötig).
  JPA-Entities/Repositories unter `ingestion.domain`
  (`PilotSession`, `PilotTrackPoint`, `PilotAirportEvent`, `AtcSession`,
  `AtcSnapshot`).
- **`monitoring`** — `HealthMonitor` merkt sich je Quelle (`vatsim-poll`,
  `ourairports-import`) den letzten Erfolgszeitpunkt; `HealthAlertService`
  prüft minütlich auf Überschreitung der Schwelle (5 Min. für den Poller)
  und verschickt E-Mail-Alerts via Spring Mail; `IngestionHealthListener`
  hört auf `PollCycleSucceededEvent`/`OurAirportsImportSucceededEvent`.
- **`app`** — Spring-Boot-Entry-Point (`VatsimStatsApplication`), verdrahtet
  alle Module, Flyway-Migrationen (`app/src/main/resources/db/migration`),
  `application.yml`. Enthält zusätzlich eine **minimale interne
  Verifikations-UI mit Vaadin** (`app.ui`: `MainLayout`,
  `PilotSessionsView`, `AtcSessionsView`) — dient nur dem manuellen Prüfen
  der aufgezeichneten Daten während der Entwicklung (Grids ohne Paging/
  Filtern/Heatmaps), **kein** Vorgriff auf die spätere Vue-Produktiv-UI und
  keine Rücknahme der Vaadin-Entscheidung oben; kann entfernt werden, sobald
  die Vue-UI dieselbe Funktionalität abdeckt.

### Datenbank & Migrationen

- **Flyway** (`spring-boot-starter-flyway`, `flyway-database-postgresql`),
  `spring.jpa.hibernate.ddl-auto: validate` — Schema wird ausschließlich
  über Migrationsskripte verändert, nie über Hibernate.
- Migrationen liegen in `app/src/main/resources/db/migration`:
  - `V1__reference_data.sql` — `airport`, `runway` (normale Tabellen).
  - `V2__ingestion.sql` — `pilot_track_point` und `atc_snapshot` als
    **TimescaleDB-Hypertables** (`create_hypertable(..., by_range('recorded_at'))`,
    Composite-PK `(id, recorded_at)`); `pilot_session`, `pilot_airport_event`,
    `atc_session` als normale (nicht-hypertable) Tabellen für abgeleitete
    Daten.
- Natürlicher Session-Schlüssel: **CID + Callsign + `logon_time`**
  (`SessionKey` in `ingestion.session`).
- DB läuft lokal via `docker-compose.yml` (Image
  `timescale/timescaledb:latest-pg16`), Zugangsdaten über `.env`
  (`.env.example` ist committed, `.env` ist `.gitignore`t).

## Separates Lernprojekt: Custom JS/TS-Komponenten in Vaadin

Unabhängig vom Hauptprojekt besteht Interesse, zu lernen, wie man
JavaScript/TypeScript-Bibliotheken als Komponenten in Vaadin einbindet, die
dann aus Java heraus nutzbar sind.

**Funktionsprinzip:**
- Vaadin-Komponenten sind im Kern Web Components (Custom Elements)
- Java hält den Server-State, das Element im Browser ist die Anzeige-Schicht
- Zwei Integrationswege:
  1. Bestehende Web Component wrappen (einfacher)
  2. Reine JS-Bibliothek (z. B. MapLibre) selbst in eine LitElement-Klasse
     kapseln, dann per `@Tag` + `@JsModule` in Java anbinden;
     Methodenaufrufe über `getElement().callJsFunction(...)`,
     Events zurück nach Java über `@DomEvent`

**Aufwandseinschätzung:**
- Einfacher Wrapper (z. B. Icon-Bibliothek): Stunden
- Interaktive Karte mit Events und Heatmap-Layer: ein bis wenige Tage
  (erstmalige Einarbeitung), danach schneller durch wiederholbares Muster
- Replay mit Zeitsteuerung (Play/Pause/Speed): aufwendigster Teil wegen
  State-Synchronisation zwischen Java (Zeitindex, Geschwindigkeit) und
  JS (Animation-Loop)

**Vorgeschlagenes Übungsprojekt:** Minimaler Vaadin-Wrapper um MapLibre GL
mit einfacher Heatmap — überschaubar genug zum Lernen, direkt wiederverwendbar
für das VATSIM-Tool, falls man sich später doch für Vaadin entscheidet oder
Teile davon prototypisch testen will.

## Build & Test

`mvn` ist auf dieser Maschine nicht im PATH, und der Standard-`JAVA_HOME` zeigt
auf eine zu alte Java-Version (dieses Projekt braucht Java 21). Beides explizit
setzen:

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot"
MVN="/c/Users/mbranz/AppData/Local/Programs/IntelliJ IDEA Ultimate/plugins/maven-plugin/lib/maven3/bin/mvn.cmd"
```

```bash
"$MVN" -q clean install          # gesamter Build inkl. Tests, alle Module
"$MVN" -q -pl app -am test -Dtest=ClassName   # einzelne Testklasse
```

**Lokale Datenbank starten** (vor `spring-boot:run` bzw. Integrationstests
mit Testcontainers/echter DB nötig; `.env` mit `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `POSTGRES_DB` muss vorher aus `.env.example` angelegt
sein):

```bash
docker compose up -d      # startet PostgreSQL+TimescaleDB (timescale/timescaledb:latest-pg16)
```

**App lokal starten** (verbindet sich gegen die per Compose gestartete DB,
kein Container-Zwang für die App selbst; Vaadin-Verifikations-UI dann unter
`http://localhost:8080`):

```bash
"$MVN" -q clean install -DskipTests   # einmalig: alle Module ins lokale Repo installieren
"$MVN" -pl app org.springframework.boot:spring-boot-maven-plugin:run
```

**Achtung:** `-pl app -am spring-boot:run` (Prefix-Form) schlägt fehl mit
`No plugin found for prefix 'spring-boot'` — mit `-am` behandelt Maven das
Parent-POM als "aktuelles Projekt" für die Prefix-Auflösung, wo das Plugin
nicht deklariert ist. Stattdessen entweder Module vorher separat
installieren und `-pl app` **ohne** `-am` verwenden, oder direkt die volle
Plugin-Koordinate (`org.springframework.boot:spring-boot-maven-plugin:run`)
angeben wie oben.

## Offene Punkte

- [ ] Ist das Replay-Feature tatsächlich im Scope? (Noch nicht final entschieden)
- [ ] Datenvolumen abschätzen (Anzahl gleichzeitiger Sessions, Historie in
      Jahren) — relevant für TimescaleDB-Konfiguration und Tabellen-Paging
      (Kompressions-/Retention-Policy noch offen)
- [ ] Entscheidung: Nuxt (SSR) nötig für öffentliche Event-Statistikseiten,
      oder reicht ein reines Vite+Vue SPA?
- [ ] UI/Frontend (Vue 3) ist noch nicht begonnen — Ingestion & Phasenerkennung
      sind bewusst als eigenständiges erstes Teilprojekt ohne UI umgesetzt
      (siehe Design-Doc).
- [ ] Runway-genaue Zuordnung von Start-/Landeereignissen (aktuell nur
      Airport-Ebene).
