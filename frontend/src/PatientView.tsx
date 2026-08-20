import { interpLabel, StatusChip } from "./StatusChip";
import type { Catalog, CdsError, PatientChart, Role } from "./types";

function formatCreatinine(value: number): string {
  return value.toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 2 });
}

function formatEgfr(value: number): string {
  return Math.round(value).toLocaleString("de-DE");
}

/** CPOE Storno: LAB in PLACED/IN_LAB, MED in ACTIVE. Not BLOCKED, RESULTED, CANCELLED. */
function isCancellable(kind: string, status: string): boolean {
  if (kind === "LAB") {
    return status === "PLACED" || status === "IN_LAB";
  }
  return kind === "MEDICATION" && status === "ACTIVE";
}

export function PatientView({
  patient,
  catalog,
  role,
  step,
  busy,
  cds,
  labOverlap,
  optimisticLock,
  onEnter,
  onReload,
  onPlaceLab,
  onTryAmox,
  onOrderCefu,
  onPlaceOtherMed,
  onOverrideAmts,
  onCancel,
}: {
  patient: PatientChart;
  catalog: Catalog | null;
  role: Role;
  step: number;
  busy: boolean;
  cds: CdsError | null;
  labOverlap: boolean;
  optimisticLock: boolean;
  onEnter: (username: string) => void;
  onReload: () => void;
  onPlaceLab: (code: string) => void;
  onTryAmox: () => void;
  onOrderCefu: () => void;
  onPlaceOtherMed: (code: string) => void;
  onOverrideAmts: () => void;
  onCancel: (orderId: string) => void;
}) {
  const warningAlerts = patient.alerts.filter((alert) => alert.severity === "WARNING" && !alert.overridden).slice(0, 2);

  return (
    <>
      <section className="card">
        <div className="kicker">
          {patient.mrn} · {patient.ward} Bett {patient.bed}
        </div>
        <h2 style={{ margin: "4px 0 8px" }}>
          {patient.familyName}, {patient.givenName}
        </h2>
        <p className="muted">
          * {patient.birthDate} · {patient.sex === "F" ? "weiblich" : "männlich"} · Aufnahme{" "}
          {new Date(patient.admittedAt).toLocaleString("de-DE")}
        </p>
        <p>
          <strong>{patient.workingDiagnosis}</strong> — {patient.chiefComplaint}
        </p>
        <div className="chips">
          {patient.allergies.map((allergy) => (
            <span key={allergy.substance} className="chip high">
              Allergie {allergy.substance} · ATC {allergy.atcPrefix}
            </span>
          ))}
        </div>
        {(patient.creatinineMgDl != null || patient.egfrMlMin != null) && (
          <div className="renal">
            <div className="kicker">Nierenfunktion</div>
            {patient.creatinineMgDl != null && (
              <div className="renal-item">
                <span>Kreatinin</span>
                <strong>
                  {formatCreatinine(patient.creatinineMgDl)} <em>mg/dl</em>
                </strong>
              </div>
            )}
            {patient.egfrMlMin != null && (
              <div className="renal-item">
                <span>eGFR (CKD-EPI 2021)</span>
                <strong>
                  {formatEgfr(patient.egfrMlMin)} <em>ml/min/1,73 m²</em>
                </strong>
              </div>
            )}
          </div>
        )}
        {role === "NURSE" && (
          <p className="nurse-cpoe" role="status">
            Pflege: kein CPOE, Akte nur lesend (RBAC).
          </p>
        )}
        {role === "LAB" && (
          <p className="nurse-cpoe" role="status">
            Labor hat kein CPOE — Anordnung nur als Ärztin (RBAC).
          </p>
        )}
        {role !== "PHYSICIAN" && patient.demoStar && step >= 4 && (
          <div className="row" style={{ marginTop: 12 }}>
            <button type="button" className="primary" onClick={() => onEnter("weber")}>
              Als Ärztin weiter (AMTS)
            </button>
          </div>
        )}
        {role === "PHYSICIAN" && patient.demoStar && step === 3 && (
          <div className="row" style={{ marginTop: 12 }}>
            <button type="button" className="primary" onClick={() => onEnter("hoffmann")}>
              Rolle Labor — Befund freigeben
            </button>
          </div>
        )}
      </section>
      {labOverlap && (
        <section className="alert overlap" role="status" aria-live="polite">
          <div className="kicker">HTTP 409</div>
          <strong>Überlappendes Laborpanel</strong>
          <p>
            Ein offener Laborauftrag deckt dieselbe Messung bereits ab (BBCRP umfasst Blutbild und CRP). Der zweite
            Auftrag wird nicht angenommen.
          </p>
        </section>
      )}
      {optimisticLock && (
        <section className="alert lock" role="status" aria-live="polite">
          <div className="kicker">HTTP 409 · Optimistic Lock</div>
          <strong>Auftrag wurde parallel geändert</strong>
          <p>Auftrag wurde parallel geändert, bitte neu laden.</p>
          <div className="row">
            <button type="button" className="primary" onClick={onReload}>
              Akte neu laden
            </button>
          </div>
        </section>
      )}
      {cds && (
        <section className="alert" role="alert" aria-live="assertive">
          <div className="kicker">HTTP 409 · CDS</div>
          <strong>AMTS-Sperre</strong>
          <p>{cds.alerts[0]?.message ?? cds.message}</p>
          {cds.alerts.slice(1).map((alert) => (
            <p key={alert.ruleId}>{alert.message}</p>
          ))}
          <div className="chips" style={{ marginBottom: 10 }}>
            {cds.alerts.map((alert) => (
              <span key={alert.ruleId} className="chip high">
                {alert.ruleId}
              </span>
            ))}
          </div>
          <div className="row">
            <button type="button" className="primary" onClick={onOrderCefu} disabled={busy || role !== "PHYSICIAN"}>
              Stattdessen Cefuroxim (J01D)
            </button>
            <button type="button" className="quiet" onClick={onOverrideAmts} disabled={busy || role !== "PHYSICIAN"}>
              Sperre dokumentiert überschreiben
            </button>
          </div>
        </section>
      )}
      {!cds &&
        warningAlerts.map((alert) => (
          <section key={alert.id} className="alert warn">
            <div className="kicker">AMTS-Hinweis</div>
            <strong>{alert.title}</strong>
            <p>{alert.message}</p>
          </section>
        ))}
      <section className="split">
        <article className="card">
          {role === "PHYSICIAN" ? (
            <>
              <h3>Laborauftrag (CPOE)</h3>
              <p className="muted">
                Arzt löst ORM^O01 aus; das Labor antwortet mit ACK und später ORU^R01. Offene Doppelaufträge und
                überlappende Panels (BBCRP ⊃ BB/CRP) enden mit HTTP 409.
              </p>
              <div className="row">
                {catalog?.labs.map((item) => (
                  <button
                    key={item.code}
                    type="button"
                    className={item.code === "BBCRP" ? "primary" : "ghost"}
                    disabled={busy}
                    onClick={() => onPlaceLab(item.code)}
                  >
                    {item.display}
                  </button>
                ))}
              </div>
              <h3>Verordnung (AMTS)</h3>
              <p className="muted">Allergie-Match gegen Penicillin (ATC J01C) ist eine harte AMTS-Sperre (HTTP 409).</p>
              <div className="row">
                <button type="button" className="danger" disabled={busy} onClick={onTryAmox}>
                  Amoxicillin — Allergie-Check
                </button>
                {catalog?.meds
                  .filter((item) => item.code !== "AMOX")
                  .map((item) => (
                    <button
                      key={item.code}
                      type="button"
                      className="ghost"
                      disabled={busy}
                      onClick={() => (item.code === "CEFU" ? onOrderCefu() : onPlaceOtherMed(item.code))}
                    >
                      {item.display}
                    </button>
                  ))}
              </div>
            </>
          ) : (
            <>
              <h3>Auftragswesen</h3>
              <p className="muted">CPOE und Verordnung sind der Rolle Ärztin vorbehalten. Die Akte bleibt lesbar.</p>
            </>
          )}
        </article>
        <article className="card">
          <h3>Aufträge und Befunde</h3>
          <table>
            <thead>
              <tr>
                <th>Auftrag</th>
                <th>Status</th>
                <th>Werte</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {patient.orders.map((order) => (
                <tr
                  key={order.id}
                  className={
                    order.status === "BLOCKED" ? "blocked-row" : order.status === "CANCELLED" ? "cancelled-row" : ""
                  }
                >
                  <td>
                    {order.displayName}
                    <div className="muted">
                      {order.kind} {order.atc ?? ""}
                    </div>
                  </td>
                  <td>
                    <StatusChip status={order.status} />
                  </td>
                  <td>
                    {order.observations.map((obs) => (
                      <div
                        key={obs.code}
                        className={obs.interpretation && obs.interpretation !== "N" ? "chip high" : ""}
                        style={{ display: "block", marginBottom: 4 }}
                      >
                        {obs.display} {obs.value} {obs.unit} · {interpLabel(obs.interpretation)}
                      </div>
                    ))}
                  </td>
                  <td>
                    {role === "PHYSICIAN" && isCancellable(order.kind, order.status) && (
                      <button
                        type="button"
                        className="ghost"
                        disabled={busy}
                        title="Auftrag stornieren"
                        onClick={() => onCancel(order.id)}
                      >
                        Stornieren
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </article>
      </section>
    </>
  );
}
