import { StatusChip } from "./StatusChip";
import type { Role, WorklistItem } from "./types";

export function LabWorklist({
  worklist,
  role,
  busy,
  onAccept,
  onRelease,
  onReleaseDemo,
}: {
  worklist: WorklistItem[];
  role: Role;
  busy: boolean;
  onAccept: (orderId: string) => void;
  onRelease: (orderId: string) => void;
  onReleaseDemo: () => void;
}) {
  const canAct = role !== "NURSE";
  return (
    <section className="card">
      <h2>Labor-Worklist</h2>
      <p className="muted">Annahme: Status in Analytik. Freigabe schreibt Messwerte (LOINC) und ORU^R01.</p>
      {role === "NURSE" && (
        <p className="nurse-cpoe" role="status">
          Pflege: Labor-Worklist nur lesend (RBAC). Annahme und Befundfreigabe durch MTA.
        </p>
      )}
      {worklist.length === 0 && <p className="muted">Keine offenen Laboraufträge.</p>}
      <table>
        <thead>
          <tr>
            <th>Patient</th>
            <th>Auftrag</th>
            <th>Status</th>
            {canAct && <th></th>}
          </tr>
        </thead>
        <tbody>
          {worklist.map((item) => (
            <tr key={item.orderId} className={item.demoStar ? "demo-row" : ""}>
              <td>
                {item.patientName} · Bett {item.bed}
                {item.demoStar && <div className="chip star">Demo</div>}
              </td>
              <td>{item.displayName}</td>
              <td>
                <StatusChip status={item.status} />
              </td>
              {canAct && (
                <td className="row">
                  {item.status === "PLACED" && (
                    <button type="button" className="ghost" disabled={busy} onClick={() => onAccept(item.orderId)}>
                      Annehmen
                    </button>
                  )}
                  <button
                    type="button"
                    className="primary"
                    disabled={busy}
                    onClick={() => (item.demoStar ? onReleaseDemo() : onRelease(item.orderId))}
                  >
                    Befund freigeben
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
