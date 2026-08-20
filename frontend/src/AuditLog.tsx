import type { AuditEvent } from "./types";

const ROLE_LABEL: Record<string, string> = {
  PHYSICIAN: "Ärztin/Arzt",
  LAB: "Labor",
  NURSE: "Pflege",
};

function formatWhen(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("de-DE");
}

export function AuditLog({ events }: { events: AuditEvent[] }) {
  return (
    <article className="card">
      <h2>Audit-Protokoll</h2>
      <p className="muted">Nachvollziehbar nach CPOE, AMTS und Laborfreigabe.</p>
      {events.length === 0 ? (
        <p className="muted">Noch keine Audit-Ereignisse.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Wer</th>
              <th>Rolle</th>
              <th>Aktion</th>
              <th>Detail</th>
              <th>Zeit</th>
            </tr>
          </thead>
          <tbody>
            {events.map((event, index) => (
              <tr key={event.id || `audit-${index}`}>
                <td>{event.actor ?? ""}</td>
                <td>{ROLE_LABEL[event.actorRole ?? ""] ?? event.actorRole ?? ""}</td>
                <td>{event.action ?? ""}</td>
                <td>{event.detail ?? ""}</td>
                <td>{formatWhen(event.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </article>
  );
}
