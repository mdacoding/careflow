# Careflow

Klinischer **Stationsarbeitsplatz** (CPOE) für das fiktive Musterklinikum Nord: Laborauftrag, Befundrücklauf, AMTS, HL7 v2 und FHIR R4. Nur synthetische Demodaten.

Spring-Boot-Backend und React/TypeScript-UI in einem Repository; der UI-Build liegt im JAR unter `/`.

[![CI](https://github.com/mdacoding/careflow/actions/workflows/ci.yml/badge.svg)](https://github.com/mdacoding/careflow/actions/workflows/ci.yml)
[![Render Free](https://img.shields.io/badge/Deploy-Render%20Free-1a7a6d)](https://render.com/deploy?repo=https://github.com/mdacoding/careflow)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![React](https://img.shields.io/badge/React-19-61dafb)
![FHIR](https://img.shields.io/badge/FHIR-R4-0f4c5c)
![HL7](https://img.shields.io/badge/HL7-v2%20ORM%2FORU-0f4c5c)
![License](https://img.shields.io/badge/License-MIT-blue)

## 5-Minuten-Demo

1. **Dr. med. Lena Weber** (Passwort `demo`).
2. **Elena Krüger** — Bett 12, Allergie Penicillin, Verdacht Pneumonie.
3. Laborauftrag **Blutbild + CRP** → `ORM^O01` plus ACK.
4. Rolle Labor: annehmen, **Befund freigeben** → `ORU^R01`.
5. CRP pathologisch. **Amoxicillin** → AMTS-Sperre (ATC-Hierarchie J01C).
6. **Cefuroxim** → Hinweis Kreuzallergie β-Laktam. Ansicht **HL7 / FHIR**.

Optional zweiter Fall: **Karl-Heinz Vogt**, Herzinsuffizienz NYHA III. **Ibuprofen** → AMTS-Hinweis NSAR; Niere über CKD-EPI, sobald Kreatinin befundet ist.

Kennungen: `weber` Ärztin, `hoffmann` MTA, `schmidt` Pflege — Passwort `demo`. RBAC: Pflege ohne CPOE.

## Screenshots

Synthetische Demo, aufgenommen lokal.

| Login | Station Innere 3 |
|---|---|
| ![Login](docs/screenshots/01-login.png) | ![Stationsboard](docs/screenshots/02-station.png) |
| **AMTS-Sperre** Elena / Penicillin | **Interop** HL7 v2 + FHIR R4 |
| ![AMTS](docs/screenshots/05-amts-sperre.png) | ![HL7 FHIR](docs/screenshots/06-interop.png) |

Akte und Labor-Worklist: `docs/screenshots/03-akte-elena.png`, `docs/screenshots/04-labor.png`.

## Live-Demo

Noch keine öffentliche Instanz: `careflow.onrender.com` antwortet mit 404, solange das Render-Blueprint nicht verbunden ist. Deploy auf Render Free:

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/mdacoding/careflow)

Nach dem Deploy (Render Free schläft nach Idle; erster Request dauert länger; H2 startet leer, Seeder legt sechs Fälle an):

| Einstieg | Pfad |
|---|---|
| Stationsarbeitsplatz | `/` |
| FHIR Patient | `/fhir/Patient?_format=json` |
| FHIR Observation | `/fhir/Observation?patient={id}&_format=json` |
| FHIR metadata | `/fhir/metadata?_format=json` |
| OpenAPI | `/swagger-ui.html` |
| Health | `/actuator/health` |

Akte zeigt Kreatinin/eGFR; Interop das Audit-Protokoll.

## Tech-Stack

**Backend**
- Java 21, Spring Boot 3.4 (Web, WebSocket, Security/RBAC, Data JPA, Validation, Actuator)
- Flyway, Hibernate (`ddl-auto: validate`), Optimistic Locking (`@Version` auf Aufträgen)
- H2 im Demo-Betrieb, PostgreSQL 16 lokal per Docker Compose
- springdoc-openapi

**Interop**
- HAPI HL7 v2.5: `ORM^O01` (ORC NW / SC / CA), `ORU^R01` (ORC CM), `ACK` (PipeParser, ohne Validating)
- Ausgang ORM NW/CA von CAREFLOW, Eingang SC/ORU von LABSYS, ACK jeweils vom Empfänger. FHIR-Suche gebatched.
- HAPI FHIR 7.6 R4 RestfulServer als Lese-Projektion (Search/Read/metadata), kein Create: Patient, Encounter, AllergyIntolerance, ServiceRequest, Observation, DiagnosticReport, MedicationRequest
- FHIR Collection-Bundle je Akte unter `/api/patients/{id}/fhir`

**Fachlogik**
- Auftrags-Zustandsmaschine (LAB: PLACED → IN_LAB → RESULTED; MED: ACTIVE / BLOCKED)
- Storno: LAB in PLACED/IN_LAB und MED in ACTIVE → CANCELLED
- Optimistic Locking sichtbar als HTTP 409 `OPTIMISTIC_LOCK`
- AMTS-Regelengine: ATC-Hierarchie (Allergie-Prefix, chemische 5-Stellen-Gruppe), Kreuzallergie J01C/J01D, NSAR bei Herzinsuffizienz
- AMTS-Sperre (HTTP 409 `CDS_BLOCK`) kann die Ärztin dokumentiert überschreiben (`override`): Auftrag bleibt `BLOCKED`, Audit/Alert `overridden`. Der 5-Minuten-Pfad nimmt Cefuroxim, nicht den Override.
- Niere: CKD-EPI Kreatinin 2021 (ohne Race), NSAR-Block bei eGFR unter 30, Warnung unter 60
- Befundflags HL7 0078 (N/L/H/LL/HH) inkl. LOINC-Panic-Grenzen
- Offene Doppel-Laboraufträge und überlappende Panels (BBCRP ⊃ BB/CRP): HTTP 409
- Stationsboard, Akte und Labor-Worklist in einem Roundtrip (kein N+1)

**Frontend**
- React 19, TypeScript, Vite 6
- Views: Login, Stationsboard, Akte, Labor-Worklist, Interop
- Produktionsbuild im selben JAR (`npm run build:spring`)
- WebSocket-Ereignisse in der UI nur mit Session; HL7-Storno als ORM CA

**Qualität / Betrieb**
- Bean Validation: ungültiger Request → HTTP 400 `VALIDATION`
- CSRF absichtlich aus (Cookie-SPA + Vite-Proxy); Session HttpOnly + SameSite=Lax
- WebSocket-Origins: localhost und `https://*.onrender.com` (kein `*`)
- Demo-Session: 8 Stunden
- JUnit 5: ATC, CKD-EPI, Referenzbereich, Zustandsmaschine/Storno, HL7-Roundtrip, API (AMTS-Sperre, Override `BLOCKED`/`overridden`, VALIDATION 400, RBAC, Overlap 409, SameSite-Cookie, Kreatinin/eGFR, Audit-DTO, FHIR Search/Read ohne Create, CapabilityStatement)
- GitHub Actions (CI grün): Temurin 21, Node 22, Free Runner
- Docker Multi-Stage; Render Free, ein Dienst, H2 im Speicher
- Live-URL `careflow.onrender.com` bleibt 404, bis das Render-Blueprint verbunden ist

Kein Kafka, kein STOMP, kein Keycloak, keine bezahlte Arzneimittel-DB.

## Architektur

```mermaid
flowchart LR
  Arzt[Arzt UI] --> API[Careflow API]
  Labor[Labor UI] --> API
  API --> SM[Zustandsmaschine]
  SM --> HL7[HL7 v2 ORM/ORU]
  SM --> CDS[AMTS ATC / CKD-EPI]
  SM --> FHIR[FHIR R4 Projektion]
```

Eine Akte, drei Sichten: Station, Labor, Schnittstelle. FHIR ist Lese-Projektion (Search/Read/metadata), nicht zweites Primärsystem.

## Lokal starten

```bash
./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

UI: http://localhost:5173 — API: http://localhost:8080/swagger-ui.html

UI im JAR:

```bash
cd frontend && npm run build:spring
./mvnw spring-boot:run
```

http://localhost:8080

## Tests

```bash
./mvnw -B test
cd frontend && npm run build
```

README-Screenshots (optional; Playwright nur lokal, keine Projekt-Dependency):

```bash
cd frontend && npm install --no-save playwright && npx playwright install chromium
cd ..
# UI im JAR, z. B. PORT=8090 ./mvnw spring-boot:run
node scripts/capture-screenshots.mjs
```

`CAREFLOW_URL` default `http://127.0.0.1:8080`.
