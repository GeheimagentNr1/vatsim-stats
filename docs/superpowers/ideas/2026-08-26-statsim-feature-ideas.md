# Ideensammlung: Statistik-Features (Inspiration statsim.net)

Status: unstrukturierte Ideensammlung, noch keine finalen Design-Entscheidungen.
Entstanden aus einem Brainstorming-Vergleich mit https://statsim.net/ (Scan am
2026-08-26). Dient als Grundlage für spätere Einzel-Brainstormings zu den
jeweiligen Kategorien (→ eigene Specs unter `docs/superpowers/specs/`).

## 1. Session-Tracking — Piloten/Flüge

- Suche nach VATSIM-ID / Callsign / Zeitraum
- Top-Liste Bewegungen pro Airport (Departures/Arrivals/Total), Top 100, mit
  Zeitraum-Presets (heute/Woche/Monat/Jahr/custom Range)
- Flüge pro Land (Departed/Arrived/Total)
- Routen-Statistik: meistgeflogene Strecken zwischen zwei Airports (z. B.
  letzte 12 Monate), mit Departure/Arrival-Suche
- Airport-Detailseite pro ICAO

## 2. Session-Tracking — ATC

- Bookings (nächste 24h, Chart + Tabelle, sortierbar nach Zeit/Position)
- Time Online (pro Position + kombiniert über alle Positionen eines
  Controllers)
- Sessions (gruppiert nach Position und nach VATSIM-ID)
- Integration mit offiziellem VATSIM-Booking-System

Piloten und ATC sind bewusst als getrennte Kategorien geführt (unterschiedliche
Entitäten/Fragestellungen — passend zu unserer bestehenden Trennung
`PilotSession`/`PilotTrackPoint`/`PilotAirportEvent` vs. `AtcSession`/
`AtcSnapshot`), auch wenn sie strukturell ähnliche Muster nutzen können
(Filterung nach VATSIM-ID/Zeitraum/Position, gleiche Tabellen-/Chart-
Infrastruktur).

## 3. Live-Ansichten

- Live-Airport-Statistik ab wählbarem Datum/Uhrzeit, automatische
  Aktualisierung bei neuen Ab-/Anflügen (WebSocket/SSE)
- Live-Top-Liste während laufender Events (laufend neu sortiert)
- Overview/Dashboard: aktive ATC-Positionen (24h), aktive Flüge (24h),
  aktivste Position, längster Flug (24h)

## 4. Event-Heatmaps

- Heatmap: wo sind Flieger während eines Events entlanggeflogen
- Für offizielle VATSIM-Events UND Custom Events (eigene Event-Definition:
  Zeitraum, ggf. Ziel-Airports/Region) — statsim bietet dafür eine
  "Custom Event"-Option
- Events-Übersicht: kommende (24h) / vergangene, Chart- + Tabellenansicht

## 5. Replay

- Nachgeflogene Flüge auf Karte mit Zeitsteuerung (Play/Pause/Speed) — noch
  offen, ob im Scope (siehe offene Punkte im Projekt-CLAUDE.md)
- Bei statsim in der Navigation vorhanden, Detailseite beim Scan nicht direkt
  erreichbar (404) — vermutlich pro Flug/Event statt globale Seite

## 6. Plattform / Meta

- Öffentliche API für Entwickler
- Transparente Datenherkunft (seit wann welche Daten erfasst werden,
  Retention-Hinweis)
- Polling-Frequenz als Qualitätsmerkmal kommunizieren (bei uns: 15s)

## 7. Rechtliches / Compliance

Ausdrücklich **kein** Analytics/Tracking auf der Seite geplant — die
folgenden Punkte betreffen ausschließlich die Erhebung/Speicherung der
VATSIM-Daten selbst (CID, Callsign, Positionsverlauf, Session-Zeiten), die
bereits im Ingestion-Modul passiert, unabhängig von einer öffentlichen UI.

### DSGVO-Grundlage

Nicht die Anzeige/Statistik-Seite ist das DSGVO-Thema, sondern schon die
Erhebung und dauerhafte Speicherung der VATSIM-Daten in unserer eigenen DB:

- VATSIM-CID + Callsign + Positionsverlauf sind personenbezogene Daten
  (Art. 4 Nr. 1 DSGVO) — die CID ist ein pseudonymes, aber eindeutig einer
  natürlichen Person zuordenbares Kennzeichen.
- Rechtsgrundlage: voraussichtlich berechtigtes Interesse
  (Art. 6 Abs. 1 lit. f DSGVO) — muss in der Datenschutzerklärung begründet
  werden (Interessenabwägung).
- Betroffenenrechte müssen umsetzbar sein: Auskunft (Art. 15), Löschung
  (Art. 17), Widerspruch (Art. 21) — praktisch ein Opt-out-/
  Auskunfts-/Löschmechanismus pro VATSIM-ID.
- Lösch-/Aufbewahrungskonzept für Rohdaten (Trackpoints) nötig, z. B.
  Zeitgrenze für Rohdaten, danach nur aggregiert/anonymisiert.
- Server-/Zugriffslogs (IP-Adressen) sind ebenfalls personenbezogen und
  gehören mit Löschfrist in die Datenschutzerklärung.
- Dass VATSIM die Daten öffentlich über die eigene API bereitstellt,
  befreit uns nicht von unserer eigenen Verantwortlichkeit als
  "Verantwortlicher" für die Weiterverarbeitung/Speicherung/Darstellung.
- DSGVO-Haftung (Art. 82) ist verschuldensunabhängig und unabhängig davon,
  ob das Projekt kommerziell betrieben wird oder nicht.

### Benötigte Seiten

Rechtlich zwingend sind nur zwei Seiten:

- **Impressum** (Pflicht nach § 5 DDG, ehem. TMG, auch bei
  nicht-kommerziellen Projekten sobald öffentlich erreichbar)
- **Datenschutzerklärung** (Pflicht nach Art. 13 DSGVO)

Eine eigene "Rechtliche Hinweise"-Seite ist **keine** eigenständige Pflicht.
Üblich, aber ins Impressum integrierbar statt eigener Seite:

- Haftungsausschluss/Disclaimer (externe Links, Richtigkeit der
  Statistikdaten)
- Urheberrechtshinweis für eigene Inhalte/Karten/Grafiken
- Non-Affiliation-Hinweis ("nicht offiziell mit VATSIM verbunden") — gehört
  in Impressum oder Footer, keine eigene Seite nötig

Eine zusätzliche eigene Seite ist nur sinnvoll, falls die öffentliche API
(Punkt 6) umgesetzt wird → dann eigene Nutzungsbedingungen/ToS für die API
(Rate Limits, erlaubte Nutzung, Verfügbarkeits-Disclaimer), losgelöst vom
allgemeinen "rechtliche Hinweise"-Thema.

### Haftungsausschluss für die API

Nicht kommerziell zu sein befreit nicht automatisch von Haftung — es gibt
keine Regel im deutschen Recht, die private/unentgeltliche Angebote
grundsätzlich davon ausnimmt (§ 280, § 823 BGB gelten grundsätzlich auch
hier; die BGH-Rechtsprechung zu reduzierter Haftung bei unentgeltlichen
"Gefälligkeitsverhältnissen" ist eine Auslegungsfrage im Einzelfall, kein
verlässlicher Schutz). Ein expliziter Haftungsausschluss ist deshalb
empfehlenswert (kein separates Dokument nötig — ein kurzer Absatz im
Impressum oder in der API-Doku reicht): Daten ohne Gewähr, keine Garantie
für Verfügbarkeit/Richtigkeit, Nutzung auf eigenes Risiko, Haftung
ausgeschlossen außer bei Vorsatz/grober Fahrlässigkeit. Vergleichbar mit dem
"AS IS, WITHOUT WARRANTY"-Standardsatz in Open-Source-Lizenzen (MIT,
Apache).

### Tools zur Texterstellung (auch für englische Texte)

- **iubenda** — empfohlen für unseren Fall: internationale Ausrichtung,
  generiert Texte in 27 Sprachen inkl. Englisch bei Firmensitz Deutschland,
  DSGVO-Klauseln, Anwaltsteam im Hintergrund. Passt zu unserem primär
  englischsprachigen, aber rechtlich in Deutschland verankerten Projekt.
- **eRecht24** — deutscher Marktführer, kostenlose Basis-Generatoren für
  Impressum/Datenschutzerklärung, englische Versionen nur im Premium-Abo,
  primär deutschsprachig ausgerichtet.
- Beide Tools ersetzen keine Rechtsberatung im engeren Sinne — Antworten in
  den Fragebögen müssen zur tatsächlichen technischen Architektur passen
  (TimescaleDB-Retention, Ingestion-Frequenz etc.); vor Public-Go-Live
  Gegenlesen durch Fachanwalt für IT-Recht/Datenschutzrecht empfohlen,
  insbesondere wegen der Personenbezug-Frage bei VATSIM-Daten und weil
  Impressum-Fehler in Deutschland ein klassisches Abmahn-Ziel sind.

Keine Rechtsberatung — vor Public-Go-Live mit tatsächlicher juristischer
Prüfung abgleichen.

## Quelle

Scan von https://statsim.net/ (Overview, ATC, Flights, Events, About sowie
Unterseiten Airports/VATSIM-ID/Routes/Countries/Sessions) am 2026-08-26 via
WebFetch.
