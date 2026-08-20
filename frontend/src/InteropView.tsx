import type { KeyboardEvent } from "react";
import { AuditLog } from "./AuditLog";
import { MshRoute, OrcChip } from "./Hl7Interop";
import type { AuditEvent, Hl7View } from "./types";

export function InteropView({
  messages,
  liveHl7,
  fhir,
  audit,
  patientId,
  onSelectHl7,
  onLoadFhir,
}: {
  messages: Hl7View[];
  liveHl7: Hl7View | undefined;
  fhir: string;
  audit: AuditEvent[];
  patientId: string | undefined;
  onSelectHl7: (id: string) => void;
  onLoadFhir: (id: string) => void;
}) {
  function selectRow(event: KeyboardEvent<HTMLTableRowElement>, id: string) {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onSelectHl7(id);
    }
  }

  return (
    <>
      <p className="muted">Audit nach CPOE, AMTS und Labor — jede Aktion im Protokoll darunter.</p>
      <section className="split">
        <article className="card">
          <h2>HL7 v2 (ORM / ORU / ACK)</h2>
          <p className="muted">
            MSH-3 sendende App, MSH-5 empfangende App: Ausgang <code>CAREFLOW → LABSYS</code>, Eingang{" "}
            <code>LABSYS → CAREFLOW</code>. ORC: NW neu, SC in Analytik, CA storniert, CM befundet. ACK ohne ORC.
          </p>
          <table aria-label="HL7 v2 Nachrichten ORM ORU ACK">
            <thead>
              <tr>
                <th>Zeit</th>
                <th>Richtung</th>
                <th>Typ</th>
                <th>MSH</th>
                <th>ORC</th>
              </tr>
            </thead>
            <tbody>
              {messages.map((message) => (
                <tr
                  key={message.id}
                  className={liveHl7?.id === message.id ? "demo-row hl7-row" : "hl7-row"}
                  tabIndex={0}
                  aria-current={liveHl7?.id === message.id ? "true" : undefined}
                  onClick={() => onSelectHl7(message.id)}
                  onKeyDown={(event) => selectRow(event, message.id)}
                >
                  <td>{new Date(message.createdAt).toLocaleTimeString("de-DE")}</td>
                  <td>{message.direction === "OUTBOUND" ? "Ausgang" : "Eingang"}</td>
                  <td>
                    {message.messageType} {message.ackCode ?? ""}
                  </td>
                  <td>
                    <MshRoute raw={message.raw} />
                  </td>
                  <td>
                    <OrcChip raw={message.raw} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <pre>{liveHl7?.raw ?? "Noch keine HL7-Nachricht."}</pre>
        </article>
        <article className="card">
          <h2>FHIR R4 Collection-Bundle</h2>
          <p className="muted">
            Dieselbe Akte als Bundle. Zusätzlich HAPI RestfulServer unter <code>/fhir/Patient</code>.
          </p>
          <div className="row">
            {patientId && (
              <button type="button" className="primary" onClick={() => onLoadFhir(patientId)}>
                FHIR-Bundle laden
              </button>
            )}
            <a
              className="ghost"
              href="/fhir/Patient"
              target="_blank"
              rel="noreferrer"
              style={{
                padding: "8px 12px",
                textDecoration: "none",
                color: "inherit",
                border: "1px solid var(--line)",
                borderRadius: 8,
              }}
            >
              /fhir/Patient
            </a>
          </div>
          <pre>{fhir || "Akte öffnen, dann FHIR-Bundle laden."}</pre>
        </article>
      </section>
      <AuditLog events={audit} />
    </>
  );
}
