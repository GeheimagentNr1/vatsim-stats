# VATSIM-Statistik-Tool — Design: Datenaufzeichnung (Ingestion & Phasenerkennung)

Status: Approved (Design-Review abgeschlossen, 2026-08-24)

## Zweck

Erstes Teilprojekt vor der UI: zuverlässige, dauerhafte Aufzeichnung von
VATSIM-Piloten- und ATC-Sessions inklusive Flugrouten und automatischer
Erkennung von Start-/Lande-/Touch-and-Go-Ereignissen an Flughäfen. Dient als
Datenbasis für spätere Statistiken (Heatmaps, Session-Abfragen, Replay).

## Nicht-Ziele (für dieses Teilprojekt)

- Keine UI (Web-Frontend folgt als eigenes Teilprojekt).
- Keine Runway-genaue Zuordnung (nur Airport-Ebene) — spätere Erweiterung.
- Keine Nutzung der VATSIM Core API / OAuth (nur der offene Data Feed
  `data.vatsim.net/v3/vatsim-data.json`).

## Referenzdaten: OurAirports

- Täglicher automatischer Scheduled-Job lädt `airports.csv` und
  `runways.csv` von ourairports.com herunter und upsertet sie in die
  Tabellen `airport` und `runway` (ICAO/IATA, Name, Latitude, Longitude,
  Elevation in ft, Land; Runways werden bereits jetzt mitgespeichert, auch
  ohne aktuelle Nutzung, als Vorbereitung für spätere Runway-Erkennung).
- Schlägt der Import fehl (Datei nicht erreichbar, Format geändert), bleibt
  der bisherige Datenbestand unverändert bestehen; der Fehler wird geloggt
  und fließt in den Alerting-Mechanismus ein (siehe unten). Kein Blockieren
  der laufenden Ingestion.

## Datenmodell

### Rohdaten (append-only, TimescaleDB-Hypertables)

- `pilot_track_point`: Zeitstempel, CID, Callsign, `logon_time`, lat/lon,
  altitude, groundspeed, heading, transponder, QNH sowie die zum
  Zeitpunkt aktuellen Flugplanfelder (Departure/Destination/Aircraft, s.
  `aircraft_short`) — ein Datensatz pro Poll-Zyklus pro aktivem Piloten.
- `atc_snapshot`: Zeitstempel, CID, Callsign, `logon_time`, Frequenz,
  Facility-Typ, Range, lat/lon — ein Datensatz pro Poll-Zyklus pro aktivem
  Controller.
- Beide Tabellen sind reine, uninterpretierte Kopien der Feed-Antwort.
  Rohdaten werden **immer** vollständig persistiert, unabhängig vom Erfolg
  der nachgelagerten Ableitungslogik — sie sind die alleinige Quelle der
  Wahrheit und erlauben, Ableitungslogik jederzeit rückwirkend neu
  anzuwenden.

### Abgeleitete Daten

- `pilot_session`: eine Zeile pro zusammenhängender Pilotenverbindung
  (nicht zwingend ein einzelner Flug — siehe Session-Grenzen unten). CID,
  Callsign, `logon_time`, zuletzt bekannter Flugplan (geplantes
  Departure/Destination, `aircraft_short`), Session-Start/-Ende, Status
  (`ACTIVE`, `COMPLETED`). `departure`/`destination` als rein informative,
  abgeleitete Felder aus dem ersten `TAKEOFF`- bzw. letzten `LANDING`-Event
  derselben Session (s.u.) — nicht die Quelle für Bewegungsstatistik.
- `pilot_airport_event`: eine Zeile pro erkannter Flughafen-Bewegung —
  Session-Referenz, Airport (ICAO, per Nächster-Nachbar-Suche ermittelt),
  Event-Typ (`TAKEOFF`, `LANDING`, `TOUCH_AND_GO`, `LOW_APPROACH`),
  Zeitstempel (des ersten beobachteten Boden-/Übergangs-Polls). Ist die
  Quelle der Wahrheit für Flughafen-Bewegungsstatistik — jeder
  Touch-and-Go zählt als eigene Bewegung, auch innerhalb derselben Session.
- `atc_session`: eine Zeile pro ATC-Session — CID, Callsign/Position,
  `logon_time`, Facility, Login-/Logout-Zeitpunkt.

### Session-Schlüssel und -Grenzen

- Natürlicher Schlüssel für eine zusammenhängende Verbindung: **CID +
  Callsign + `logon_time`** (Callsign allein ist nur zu einem Zeitpunkt
  eindeutig, nicht über einen Tag; `logon_time` liefert die stabile
  Unterscheidung bei mehrfacher Nutzung desselben Callsigns an
  unterschiedlichen Tageszeiten).
- Session-Grenzen werden primär durch die **physische Phasenerkennung**
  bestimmt (Boden → Luft → Boden), nicht durch Flugplan-Änderungen:
  - Ändert sich der Flugplan (Departure/Destination), während die Session
    `AIRBORNE` ist (Diversion, Local-IFR/VFR-Platzrunden) → nur Update der
    laufenden Session, **keine** neue Session.
  - Eine neue Session entsteht nur, wenn (a) der Pilot mit neuer
    `logon_time` im Feed erscheint, oder (b) eine vorherige Session bereits
    `COMPLETED` ist (finale Landung erkannt, s.u.) **und** danach ein
    neuer/geänderter Flugplan gesendet wird, ohne dass disconnected wurde
    (Refile am Boden nach Landung, Vorbereitung des nächsten Flugs).
- VFR ohne Flugplan: `departure`/`destination` werden ausschließlich aus
  den positionsbasiert erkannten `pilot_airport_event`-Einträgen abgeleitet,
  nicht aus Flugplanfeldern (die dann leer/`null` sind).

## Phasenerkennung (Zustandsmaschine)

- Pro aktiver Session (Schlüssel s.o.) wird ein In-Memory-State geführt:
  `ON_GROUND`, `AIRBORNE`, `GROUND_PENDING` (Zwischenzustand zur
  Verweildauer-Beobachtung nach einer Boden-Berührung).
- Boden-Erkennung je Trackpunkt: `groundspeed < 40kt` **und**
  `altitude ≤ nächstgelegener_airport.elevation + 200ft`. Nächstgelegener
  Airport per Haversine-Distanz aus der `airport`-Tabelle, Kandidaten nur
  innerhalb ~5nm.
- Übergang `AIRBORNE → ON_GROUND`: State wechselt zu `GROUND_PENDING`,
  Zeitpunkt des ersten Boden-Polls wird gemerkt.
  - Bleibt der Zustand `ON_GROUND` für eine Mindestverweildauer (Default
    **90 Sekunden**, konfigurierbar) **oder** verschwindet der Pilot aus
    dem Feed, während `GROUND_PENDING` aktiv ist → Event `LANDING` wird
    geschrieben (Zeitstempel = erster Boden-Poll), Session-Status kann auf
    `COMPLETED` gesetzt werden.
  - Wird `AIRBORNE` erreicht, bevor die Verweildauer erreicht ist → Event
    `TOUCH_AND_GO` (Groundspeed war tatsächlich < 40kt) oder
    `LOW_APPROACH` (Höhe niedrig, aber Groundspeed nie unter Schwelle)
    wird geschrieben, State zurück zu `AIRBORNE`, Session bleibt `ACTIVE`.
- Übergang `ON_GROUND → AIRBORNE` (aus bestätigtem `ON_GROUND`, nicht aus
  `GROUND_PENDING`) → Event `TAKEOFF`.
- ATC-Sessions benötigen kein Phasenmodell: Session wird eröffnet, sobald
  CID+Callsign+`logon_time` neu erscheint, geschlossen, sobald es
  verschwindet (Logout-Zeitpunkt = letzter gesehener Poll). Frequenzwechsel
  auf derselben `logon_time` aktualisiert nur die laufende Session.

### Verschwinden-Erkennung mit Pufferzeit (Debounce)

- "Verschwindet aus dem Feed" (sowohl für den `GROUND_PENDING`→`LANDING`-
  Trigger als auch für das ATC-Session-Ende) wird **nicht** nach einem
  einzigen fehlenden Poll-Zyklus ausgelöst, sondern erst nach **4
  aufeinanderfolgenden verpassten Zyklen** (~60 Sekunden bei 15s-
  Poll-Intervall). Grund: ein einzelner VATSIM-Feed-Aussetzer darf nicht
  fälschlich eine Landung/einen Session-Abschluss erzeugen — 4 Zyklen
  geben einem Piloten/Controller realistisch Zeit, eine kurze
  Verbindungsstörung selbst zu beheben (Reconnect), bevor das System es
  als echtes Verschwinden wertet.
- Pro verfolgtem Schlüssel (Pilot: `SessionKey`; ATC: analog) wird ein
  Zähler für aufeinanderfolgend verpasste Zyklen geführt. Erscheint der
  Schlüssel wieder im Feed, wird der Zähler zurückgesetzt. Erst beim
  Erreichen von 4 wird die bisherige "verschwunden"-Behandlung ausgelöst
  (bei `GROUND_PENDING` → `LANDING`; bei `AIRBORNE` → nur Räumung aus dem
  Arbeitsspeicher, siehe unten; bei ATC → Session-Ende, Zeitstempel =
  letzter tatsächlich gesehener Snapshot, nicht der 4. Fehlversuch).

### AIRBORNE-Timeout (Session-Bereinigung ohne erkannte Landung)

- Verschwindet ein Pilot **während `AIRBORNE`** dauerhaft aus dem Feed
  (z. B. Verbindungsabbruch mitten im Flug, Client-Crash, Absturz aus dem
  VATSIM-Funkbereich), entsteht kein `LANDING`-Event — die Spec definiert
  hierfür bewusst kein synthetisches Event, da sonst eine Landung an
  einem Flughafen vorgetäuscht würde, die real nie stattfand. Ohne
  weitere Maßnahme bliebe die zugehörige `pilot_session` für immer auf
  Status `ACTIVE` stehen.
- Ein separater, periodischer Job (alle 5 Minuten, analog zum
  `HealthAlertService`-Muster) sucht `ACTIVE`-Pilot-Sessions, deren
  letzter bekannter Trackpunkt (`pilot_track_point`) älter als **30
  Minuten** ist, und schließt sie: Status → `COMPLETED`, `endedAt` =
  Zeitstempel des letzten bekannten Trackpunkts (nicht der Zeitpunkt des
  Jobs). **Kein** künstliches `LANDING`-Event wird erzeugt.
- Bewusste Entscheidung: kein eigener Status (z. B. `TIMED_OUT`) für
  diesen Fall — `COMPLETED` wird wiederverwendet. Die Unterscheidung
  "echte Landung erkannt" vs. "Verbindung verloren, keine Landung
  bekannt" bleibt für Auswertungen über die Existenz eines `LANDING`-
  Events in `pilot_airport_event` zur jeweiligen Session verfügbar — das
  ist bereits die primäre Quelle der Wahrheit für Bewegungsstatistik
  (siehe oben), nicht `pilot_session.status`.
- Da ein Pilot durch die 4-Zyklen-Pufferzeit (s. o.) ohnehin schon nach
  ~60 Sekunden aus dem Arbeitsspeicher geräumt wird, operiert dieser Job
  rein auf Datenbankebene und greift nicht in die laufende Live-
  Verarbeitung ein — zum Zeitpunkt des 30-Minuten-Timeouts ist die
  Session längst nicht mehr im Speicher.
- **Bekanntes Restrisiko, bewusst akzeptiert:** Fällt der VATSIM-Feed
  selbst für ≥30 Minuten am Stück aus (nicht der einzelne Pilot, sondern
  die gesamte Datenquelle), zählt der Zyklen-Zähler nicht mehr hoch (der
  Poller bricht schon beim Feed-Abruf ab, bevor `processTrackPoints`
  überhaupt läuft — Piloten bleiben also korrekt im Speicher). Der
  Timeout-Job läuft aber unabhängig auf seinem eigenen 5-Minuten-Takt
  weiter und würde nach 30 Minuten Ausfall alle noch aktiven Sessions
  fälschlich auf `COMPLETED` setzen, obwohl die Piloten (aus Systemsicht)
  weiterhin verbunden sind. Kommt der Feed danach zurück, überschreibt
  der Orchestrator diesen Stand beim nächsten Trackpunkt/Statuswechsel
  der jeweiligen Session i. d. R. automatisch wieder korrekt (der
  In-Memory-Stand ist für Live-Entscheidungen maßgeblich, nicht die
  DB-Zeile) — das Verhalten ist also größtenteils selbstheilend, nicht
  dauerhaft zerstörend. Entscheidung: **keine zusätzliche Absicherung
  eingebaut** (z. B. ein Health-Check-Gate vor dem Sweep) — ein VATSIM-
  Ausfall dieser Länge, bei dem realistisch noch relevant viele Piloten
  ununterbrochen verbunden blieben, wird als sehr unwahrscheinlich
  eingeschätzt, und der Aufwand für eine Absicherung steht in keinem
  Verhältnis zum Risiko. Sollte sich das als Fehleinschätzung erweisen,
  ist die Absicherung (Gate über den bestehenden `HealthMonitor`-
  Erfolgszeitstempel des Pollers) nachträglich ohne Architekturänderung
  ergänzbar.

## Ingestion-Poller

- `@Scheduled`-Task, alle 15 Sekunden: ruft
  `https://data.vatsim.net/v3/vatsim-data.json` ab, parst `pilots[]` und
  `controllers[]`.
- Pro Zyklus: Bulk-Insert aller Rohdatensätze in einer Transaktion, **vor**
  jeder Ableitungslogik. Damit sind Rohdaten garantiert persistiert, bevor
  irgendeine Interpretation stattfindet.
- Einzelne fehlerhafte/unvollständige Datensätze innerhalb eines Zyklus
  (z. B. Pilot ohne Position) werden übersprungen und geloggt, ohne den
  restlichen Zyklus zu verwerfen.
- Netzwerkfehler/Timeout/5xx beim Feed-Abruf: Zyklus überspringen, loggen,
  nächster Versuch in 15s (kein Retry-Sturm).
- DB kurzzeitig nicht erreichbar: Zyklus-Fehler loggen und überspringen,
  kein mehrzyklisches In-Memory-Puffern (Komplexität lohnt sich bei einem
  15s-Sample-Intervall nicht).

## Restart-Robustheit

- Da Rohdaten unabhängig vom In-Memory-State sofort persistiert werden,
  geht bei Absturz/Neustart höchstens der zuletzt nicht verarbeitete
  Poll-Zyklus an *abgeleiteten* Events verloren — nie Rohdaten.
- Beim Start lädt der Service für jeden im ersten Poll vorkommenden
  Piloten/Controller die letzten 5–10 `pilot_track_point`-Zeilen (gefiltert
  nach `logon_time`) aus der DB und rekonstruiert daraus Phase und
  `groundSinceTimestamp`. Für ATC wird die letzte offene `atc_session`
  geladen. Ohne Historie (neuer Teilnehmer) startet der State neutral.

## Alerting

- `HealthMonitor`-Service merkt sich je Datenquelle (`vatsim-poll`,
  `ourairports-import`) den Zeitpunkt des letzten erfolgreichen Laufs.
- Ein minütlicher Check prüft: liegt der letzte Erfolg von `vatsim-poll`
  länger als 5 Minuten zurück, oder ist der tägliche
  `ourairports-import` ausgeblieben/fehlgeschlagen → E-Mail-Alert (Spring
  Mail, SMTP-Konfiguration und Empfänger über `application.yml`/Env-Var).
- Alert wird nur einmal beim Überschreiten der Schwelle verschickt (kein
  Spam bei anhaltendem Ausfall); optional eine "wieder OK"-Mail beim
  Erholen.

## Projektstruktur

Maven-Multi-Modul (Spring Boot), analog zum Referenzprojekt
`vatsim-tools`, aber eigenständig für `vatsim-stats`:

- `ingestion` — Poller, VATSIM-API-Client, Rohdaten-Persistenz
- `detection` — Zustandsmaschine, Event-Ableitung (reines Java, ohne
  Spring-Abhängigkeit, unabhängig testbar)
- `reference-data` — OurAirports-Import-Job, Airport-/Runway-Repository
- `monitoring` — HealthMonitor, E-Mail-Alerting
- `app` — Spring-Boot-Entry-Point, verdrahtet alle Module

Bewusst modular geschnitten, damit die spätere UI-Schicht und ggf. weitere
Datenquellen andocken können, ohne Ingestion/Detection anzufassen.

## Lokale Entwicklungsumgebung & Deployment

- `docker-compose.yml` im Projekt-Root startet PostgreSQL mit vorinstalliertem
  TimescaleDB-Plugin (Image `timescale/timescaledb:latest-pg16`) für lokales
  Testen, mit persistentem Volume und Zugangsdaten über `.env`
  (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`) statt Hardcoding im
  Compose-File. `.env` selbst ist `.gitignore`t, `.env.example` mit
  Platzhaltern wird committed.
- Dieselbe Konfiguration (Image, Extension, Env-Var-Schema) ist Grundlage für
  den Produktivbetrieb — der Live-Server nutzt denselben TimescaleDB-Image-
  Typ, nur mit eigenen (nicht versionierten) Zugangsdaten. Kein separates
  Schema-Setup für Dev vs. Prod nötig; Datenbank-Migrationen (z. B. Flyway)
  laufen identisch gegen beide.
- Die Anwendung selbst (Spring-Boot-App) läuft lokal weiterhin direkt über
  `mvn spring-boot:run` (kein Container-Zwang für die App beim Entwickeln),
  verbindet sich aber gegen die per Compose gestartete DB.

## Verifikations-UI (Vaadin, intern)

- Zum manuellen Prüfen der aufgezeichneten Daten während der Entwicklung
  bekommt das `app`-Modul eine minimale Vaadin-Ansicht (Spring-Boot +
  Vaadin, analog zum Referenzprojekt `vatsim-tools`): je ein `Grid` für
  `pilot_session` (inkl. Drill-down auf die zugehörigen
  `pilot_airport_event`-Einträge und `pilot_track_point`-Rohdaten einer
  Session) sowie für `atc_session`.
- Bewusst **kein** Vorgriff auf die spätere Vue-Produktiv-UI — kein
  serverseitiges Paging/Filtern, keine Heatmaps/Charts, kein
  Gestaltungsanspruch. Reines internes Debug-/Verifikationswerkzeug, um
  die Ingestion- und Detection-Logik gegen echte Live-Daten zu
  beobachten, ohne die eigentliche UI vorwegzunehmen.
- Kann bei Bedarf entfernt werden, sobald die Vue-UI die gleiche
  Funktionalität abdeckt; kein Bestandteil des öffentlichen Produkts.

## Testing-Strategie

- Zustandsmaschine (Phasenerkennung, Schwellwert-Logik,
  Touch-and-Go-Klassifizierung): Unit-Tests mit synthetischen
  Trackpunkt-Sequenzen (TDD), unabhängig von Spring/DB.
- Airport-Nächster-Nachbar-Suche (Haversine): separat unit-testbar mit
  bekannten Koordinaten.
- Poller/Persistenz-Schicht: Integrationstests gegen eine echte
  Test-PostgreSQL/TimescaleDB-Instanz (Testcontainers), inklusive
  Neustart-Rekonstruktion (State aus DB neu aufbauen und mit erwartetem
  State vergleichen).
- Kein Live-Aufruf der echten VATSIM-API in Tests — injizierter/gemockter
  Client mit JSON-Fixtures (reale, ggf. anonymisierte Ausschnitte).

## Offene Punkte für spätere Teilprojekte

- Runway-genaue Zuordnung von Start-/Landeereignissen.
- UI/Frontend (Vue 3, siehe Projekt-CLAUDE.md).
- Genaue TimescaleDB-Kompressions-/Retention-Policy (Datenvolumen zunächst
  unkritisch, spätere Optimierung möglich).
- ATC-Frequenz-/Positionswechsel-Historie als eigene Detailauswertung
  (aktuell nur Login/Logout erfasst).
- Interpolation zwischen zwei `pilot_track_point`-Einträgen (z. B. für ein
  flüssigeres Replay zwischen zwei 15s-Samples) ist durch den in jedem
  Punkt gespeicherten Zeitstempel bereits möglich, ohne zusätzliche
  Speicherung — Umsetzung erfolgt bei Bedarf im Replay-Teilprojekt.
