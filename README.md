# Careflow

Klinischer **Stationsarbeitsplatz** für das fiktive Musterklinikum Nord: Auftragswesen, Laborbefund, AMTS-Prüfung, HL7 v2 und FHIR R4. Nur synthetische Demodaten, kein Arbeitgeber- und kein Patientenbezug.

Unabhängige Fullstack-Anwendung: Spring-Boot-Backend und React/TypeScript-UI im selben Repository. Der Produktionsbuild liegt im Spring-JAR und wird unter `/` ausgeliefert.

[![CI](https://github.com/mdacoding/careflow/actions/workflows/ci.yml/badge.svg)](https://github.com/mdacoding/careflow/actions/workflows/ci.yml)
[![Live](https://img.shields.io/badge/Live-Render-1a7a6d)](https://careflow.onrender.com)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![Spring Security](https://img.shields.io/badge/Spring%20Security-session%2FRBAC-6db33f)
![React](https://img.shields.io/badge/React-19-61dafb)
![FHIR](https://img.shields.io/badge/HAPI%20FHIR-R4-0f4c5c)
![HL7](https://img.shields.io/badge/HAPI%20HL7-v2.5%20ORM%2FORU-0f4c5c)
![License](https://img.shields.io/badge/License-MIT-blue)

## 5-Minuten-Demo

1. **Dr. med. Lena Weber** wählen (Passwort `demo`).
2. **Elena Krüger** öffnen (Bett 12, Penicillin-Allergie, Pneumonie-Verdacht).
3. **Blutbild + CRP** anordnen — es entsteht `ORM^O01` plus ACK.
4. Oben auf **Labor** wechseln (oder Rolle MTA) und **Befund freigeben**.
5. CRP ist pathologisch. **Amoxicillin** auslösen — AMTS sperrt wegen Allergie.
6. **Cefuroxim** wählen (Hinweis Kreuzallergie) und unter **Interop** HL7 + FHIR-Bundle zeigen.

Kennungen: `weber` (Ärztin), `hoffmann` (Labor), `schmidt` (Pflege) — Passwort jeweils `demo`.

## Live-Demo

| Einstieg | URL |
|---|---|
| Stationsarbeitsplatz | https://careflow.onrender.com |
| FHIR Patient | https://careflow.onrender.com/fhir/Patient?_format=json |
| OpenAPI | https://careflow.onrender.com/swagger-ui.html |
| Health | https://careflow.onrender.com/actuator/health |

Render Free schläft nach Inaktivität; der erste Request danach dauert länger. H2 startet leer und wird mit sechs Stationsfällen befüllt.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/mdacoding/careflow)

## Tech-Stack

Der Stack trifft typische Anforderungen in KIS-/DIZ- und Schnittstellen-Teams: FHIR-R4-REST, HL7-v2-Nachrichten, Session-Security mit Rollen und automatisierte Tests — ohne ungenutzte Middleware.

**Backend**
- Java 21, Spring Boot 3.4.5 (Web, WebSocket, Security, Data JPA, Validation, Actuator)
- Spring Security: Session-Cookie (`HttpSessionSecurityContextRepository`), In-Memory-UserDetails, Rollen `PHYSICIAN` / `LAB` / `NURSE`; fachliche RBAC in der Service-Schicht
- Spring Data JPA / Hibernate (`ddl-auto: validate`), Flyway; H2 im Demo-Betrieb, PostgreSQL 16 lokal per Docker Compose
- Native WebSocket unter `/api/ws` (kein STOMP)
- springdoc-openapi 2.8 (Swagger UI)
- Eigene AMTS-Regelengine (Allergie, Cephalosporin-Kreuzreaktion, Doppel-ATC, NSAR)

**Frontend**
- React 19, TypeScript 5.8, Vite 6 in `frontend/`
- Session-Cookie (`credentials: include`), Vite-Proxy auf `/api` und `/fhir`, Browser-WebSocket
- Produktionsbuild im selben Spring-JAR unter `/` — ein Dienst, kein zweites Frontend-Hosting

**Interop**
- HAPI FHIR 7.6 (`RestfulServer`, `FhirContext.forR4()`): Search/Read unter `/fhir` für Patient, Encounter, AllergyIntolerance, ServiceRequest, Observation, DiagnosticReport, MedicationRequest
- HAPI HL7 v2.5 (`hapi-structures-v25`, `PipeParser`): `ORM^O01` (Laborauftrag), `ORU^R01` (Befund mit LOINC/OBX), `ACK` (MSA AA)
- FHIR-Bundle derselben Akte über die Careflow-API; FHIR ist Projektion, kein zweites Wahrheitssystem

**Qualität**
- JUnit 5, MockMvc, TestRestTemplate, AssertJ
- Abgedeckt: AMTS-Regeln, Auftrags-Zustandsmaschine, HL7-Parse/Encode-Roundtrip, API inkl. AMTS-Sperre (HTTP 409)
- GitHub Actions: Backend `./mvnw -B test` (Temurin 21), Frontend `npm ci && npm run build` (Node 22)

**Betrieb**
- Docker Multi-Stage: Node 22 baut die UI, Maven (Temurin 21) packt das JAR, Runtime `eclipse-temurin:21-jre-alpine`
- Render Free: ein Web-Service aus dem Dockerfile, H2 im Speicher, Health unter `/actuator/health`

## Architektur

```mermaid
flowchart LR
  Arzt[Arzt UI] --> API[Careflow API]
  Labor[Labor UI] --> API
  API --> SM[Auftrags-Zustandsmaschine]
  SM --> HL7[HL7 v2 ORM/ORU]
  SM --> CDS[AMTS-Regeln]
  SM --> FHIR[FHIR R4 Projektion]
```

- **Station** sieht Fälle, Allergien, offene Aufträge.
- **Arzt** erzeugt Laboraufträge und Medikation; Laboraufträge gehen als HL7 `ORM^O01` raus.
- **Labor** arbeitet eine Worklist ab und liefert `ORU^R01` inkl. LOINC-Werte.
- **AMTS** prüft vor der Verordnung; Block endet als HTTP 409 mit Regel-ID.
- **FHIR** ist eine Projektion derselben Akte, kein zweites Wahrheitssystem.

## Lokal starten

Zwei Terminals, Java 21 und Node 22:

```bash
./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

UI: http://localhost:5173  
API/OpenAPI: http://localhost:8080/swagger-ui.html

Ohne Frontend-Devserver, UI im JAR:

```bash
cd frontend && npm run build
./mvnw spring-boot:run
```

Dann http://localhost:8080

## Tests

```bash
./mvnw -B test
cd frontend && npm run build
```
