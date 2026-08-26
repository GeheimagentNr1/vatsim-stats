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
bereits im Ingestion-Modul passiert, unabhängig von einer öffentlichen UI:

- Impressum
- Datenschutzerklärung (Pflicht wegen Speicherung von CID/Callsign/
  Positionsdaten, Art. 13 DSGVO)
- Rechtsgrundlage dokumentieren (voraussichtlich berechtigtes Interesse,
  Art. 6 Abs. 1 lit. f DSGVO)
- Lösch-/Aufbewahrungskonzept für Rohdaten (Trackpoints)
- Opt-out-/Auskunfts-/Löschmechanismus pro VATSIM-ID (Art. 15/17/21 DSGVO)

Keine Rechtsberatung — vor Public-Go-Live mit tatsächlicher juristischer
Prüfung abgleichen.

## Quelle

Scan von https://statsim.net/ (Overview, ATC, Flights, Events, About sowie
Unterseiten Airports/VATSIM-ID/Routes/Countries/Sessions) am 2026-08-26 via
WebFetch.
