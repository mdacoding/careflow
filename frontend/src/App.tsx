import { useEffect, useMemo, useState } from "react";
import { api, asApiError, isAmtsBlock, isIllegalState, isLabOverlap, isOptimisticLock } from "./api";
import { AuditLog } from "./AuditLog";
import { DemoGuide, demoHint } from "./DemoGuide";
import { interpLabel, StatusChip } from "./StatusChip";
import type { AuditEvent, Catalog, CdsError, DemoInfo, Hl7View, OrderView, PatientChart, Staff, WardCard, WorklistItem } from "./types";

/** CPOE Storno: LAB in PLACED/IN_LAB, MED in ACTIVE. Not BLOCKED, RESULTED, CANCELLED. */
function isCancellable(order: OrderView): boolean {
  if (order.kind === "LAB") {
    return order.status === "PLACED" || order.status === "IN_LAB";
  }
  return order.kind === "MEDICATION" && order.status === "ACTIVE";
}

function formatCreatinine(value: number): string {
  return value.toLocaleString("de-DE", { minimumFractionDigits: 1, maximumFractionDigits: 2 });
}

function formatEgfr(value: number): string {
  return Math.round(value).toLocaleString("de-DE");
}

type View = "ward" | "patient" | "lab" | "interop";

const ROLES: { username: string; label: string }[] = [
  { username: "weber", label: "Ärztin" },
  { username: "hoffmann", label: "Labor" },
  { username: "schmidt", label: "Pflege" },
];

export default function App() {
  const [staff, setStaff] = useState<Staff | null>(null);
  const [view, setView] = useState<View>("ward");
  const [demo, setDemo] = useState<DemoInfo | null>(null);
  const [step, setStep] = useState(0);
  const [ward, setWard] = useState<WardCard[]>([]);
  const [patient, setPatient] = useState<PatientChart | null>(null);
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [worklist, setWorklist] = useState<WorklistItem[]>([]);
  const [messages, setMessages] = useState<Hl7View[]>([]);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [fhir, setFhir] = useState("");
  const [selectedHl7, setSelectedHl7] = useState<string>("");
  const [flash, setFlash] = useState("");
  const [illegalFlash, setIllegalFlash] = useState("");
  const [cds, setCds] = useState<CdsError | null>(null);
  const [labOverlap, setLabOverlap] = useState(false);
  const [optimisticLock, setOptimisticLock] = useState(false);
  const [busy, setBusy] = useState(false);

  async function refreshWard() {
    setWard(await api.ward());
  }

  async function openPatient(id: string) {
    const chart = await api.patient(id);
    if (patient?.id !== id) {
      setCds(null);
      setLabOverlap(false);
      setOptimisticLock(false);
      setIllegalFlash("");
    }
    setPatient(chart);
    setView("patient");
    if (chart.demoStar && step < 2) {
      setStep(2);
    }
  }

  async function refreshContext() {
    if (!staff) {
      return;
    }
    await refreshWard();
    setCatalog(await api.catalog());
    setWorklist(await api.worklist());
    setMessages(await api.messages());
    try {
      const events = await api.audit();
      setAudit(Array.isArray(events) ? events : []);
    } catch {
      setAudit([]);
    }
    if (patient) {
      const chart = await api.patient(patient.id);
      setPatient(chart);
      setFhir(await api.fhir(chart.id));
    }
  }

  useEffect(() => {
    api.me()
      .then(async (current) => {
        setStaff(current);
        setDemo(await api.demo());
      })
      .catch(() => setStaff(null));
  }, []);

  useEffect(() => {
    if (!staff) {
      return;
    }
    void refreshContext();
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/api/ws`);
    socket.onmessage = () => {
      void refreshContext();
    };
    return () => socket.close();
    // websocket reconnects when the signed-in user changes
  }, [staff?.username]);

  const liveHl7 = useMemo(
    () => messages.find((message) => message.id === selectedHl7) ?? messages[0],
    [messages, selectedHl7],
  );

  const steps = demo?.steps ?? [];
  const hint = demoHint(step, view, staff?.role ?? "", labOverlap);

  async function enter(username: string, startDemo = false) {
    const session = await api.login(username);
    setStaff(session);
    setDemo(await api.demo());
    if (startDemo) {
      setStep(1);
      setView("ward");
      setPatient(null);
      setCds(null);
      setLabOverlap(false);
      setOptimisticLock(false);
      setIllegalFlash("");
    } else if (username === "hoffmann") {
      setView("lab");
    } else {
      setView(patient ? "patient" : "ward");
    }
    setFlash(`Angemeldet als ${session.displayName}`);
  }

  async function placeLab(code: string) {
    if (!patient) {
      return;
    }
    setBusy(true);
    try {
      await api.placeLab(patient.id, code);
      setLabOverlap(false);
      setOptimisticLock(false);
      setIllegalFlash("");
      setFlash(
        code === "BBCRP"
          ? "Laborauftrag übermittelt — HL7 ORM^O01 und ACK liegen im Interop-Log."
          : "Laborauftrag übermittelt.",
      );
      if (code === (demo?.labPreset ?? "BBCRP") && step < 3) {
        setStep(3);
      }
      await refreshContext();
    } catch (error) {
      if (isOptimisticLock(error)) {
        setOptimisticLock(true);
        setLabOverlap(false);
        setIllegalFlash("");
        setFlash("");
      } else if (isLabOverlap(error)) {
        setLabOverlap(true);
        setOptimisticLock(false);
        setFlash("");
      } else if (isIllegalState(error)) {
        setIllegalFlash(asApiError(error).message);
        setFlash("");
      } else {
        setFlash(asApiError(error).message);
      }
    } finally {
      setBusy(false);
    }
  }

  async function releaseDemo() {
    const item = worklist.find((entry) => entry.demoStar) ?? worklist[0];
    if (!item) {
      return;
    }
    setBusy(true);
    try {
      if (item.status === "PLACED") {
        await api.acceptLab(item.orderId);
      }
      await api.releaseLab(item.orderId);
      setFlash("Befund freigegeben — ORU^R01 erzeugt, LOINC-Werte in der Akte. Als Ärztin Amoxicillin prüfen.");
      setStep(4);
      await openPatient(item.patientId);
    } finally {
      setBusy(false);
    }
  }

  async function tryAmox() {
    if (!patient) {
      return;
    }
    setBusy(true);
    try {
      await api.placeMed(patient.id, "AMOX");
      setCds(null);
    } catch (error) {
      if (isAmtsBlock(error)) {
        const parsed = asApiError(error);
        setCds({
          error: "CDS_BLOCK",
          message: parsed.message,
          alerts: parsed.alerts,
        });
        setFlash("");
        setOptimisticLock(false);
        setStep(5);
      } else if (isOptimisticLock(error)) {
        setOptimisticLock(true);
        setFlash("");
        setIllegalFlash("");
      } else if (isIllegalState(error)) {
        setIllegalFlash(asApiError(error).message);
        setFlash("");
      } else {
        setFlash(asApiError(error).message);
      }
    } finally {
      setBusy(false);
      await refreshContext();
    }
  }

  async function orderCefu() {
    if (!patient) {
      return;
    }
    setBusy(true);
    try {
      await api.placeMed(patient.id, "CEFU");
      setCds(null);
      setFlash("Cefuroxim verordnet — AMTS: Kreuzallergie β-Laktam als Hinweis (kein Block).");
      setIllegalFlash("");
      setOptimisticLock(false);
      setStep(6);
      await refreshContext();
    } catch (error) {
      if (isAmtsBlock(error)) {
        const parsed = asApiError(error);
        setCds({
          error: "CDS_BLOCK",
          message: parsed.message,
          alerts: parsed.alerts,
        });
        setFlash("");
        setOptimisticLock(false);
      } else if (isOptimisticLock(error)) {
        setOptimisticLock(true);
        setFlash("");
        setIllegalFlash("");
      } else if (isIllegalState(error)) {
        setIllegalFlash(asApiError(error).message);
        setFlash("");
      } else {
        setFlash(asApiError(error).message);
      }
    } finally {
      setBusy(false);
    }
  }

  async function placeOtherMed(code: string) {
    if (!patient) {
      return;
    }
    setBusy(true);
    try {
      await api.placeMed(patient.id, code);
      setFlash("Verordnung aktiv.");
      setIllegalFlash("");
      setOptimisticLock(false);
      await refreshContext();
    } catch (error) {
      if (isAmtsBlock(error)) {
        const parsed = asApiError(error);
        setCds({
          error: "CDS_BLOCK",
          message: parsed.message,
          alerts: parsed.alerts,
        });
        setFlash("");
        setOptimisticLock(false);
      } else if (isOptimisticLock(error)) {
        setOptimisticLock(true);
        setFlash("");
        setIllegalFlash("");
      } else if (isIllegalState(error)) {
        setIllegalFlash(asApiError(error).message);
        setFlash("");
      } else {
        setFlash(asApiError(error).message);
      }
    } finally {
      setBusy(false);
    }
  }

  async function cancelOrder(orderId: string) {
    if (!patient) {
      return;
    }
    setBusy(true);
    try {
      await api.cancel(orderId);
      setOptimisticLock(false);
      setIllegalFlash("");
      setFlash("Auftrag storniert.");
      await refreshContext();
    } catch (error) {
      if (isOptimisticLock(error)) {
        setOptimisticLock(true);
        setLabOverlap(false);
        setIllegalFlash("");
        setFlash("");
        await refreshContext();
      } else if (isIllegalState(error)) {
        setIllegalFlash(asApiError(error).message);
        setFlash("");
        await refreshContext();
      } else {
        setFlash(asApiError(error).message);
      }
    } finally {
      setBusy(false);
    }
  }

  if (!staff) {
    return (
      <div className="login">
        <div className="login-card">
          <div className="kicker">Musterklinikum Nord · Innere 3</div>
          <h1>Careflow</h1>
          <p className="muted">
            Klinischer Stationsarbeitsplatz: CPOE, Laborbefund, AMTS, HL7 v2 ORM/ORU, FHIR R4. Synthetische Demodaten.
            Passwort überall <code>demo</code>.
          </p>
          <div className="login-grid">
            <button className="staff" onClick={() => void enter("weber", true)}>
              <span className="kicker">5-Minuten-Demo</span>
              <b>Dr. med. Lena Weber</b>
              <span className="muted">Oberärztin — führt den Demo-Fall Elena Krüger</span>
            </button>
            <button className="staff" onClick={() => void enter("hoffmann")}>
              <span className="kicker">Labor</span>
              <b>Tim Hoffmann</b>
              <span className="muted">MTA — Worklist, Annahme, Befundfreigabe</span>
            </button>
            <button className="staff" onClick={() => void enter("schmidt")}>
              <span className="kicker">Pflege</span>
              <b>Paula Schmidt</b>
              <span className="muted">Station — lesend, ohne CPOE</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  const warningAlerts = (patient?.alerts ?? []).filter((alert) => alert.severity === "WARNING" && !alert.overridden).slice(0, 2);

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="logo">Cf</div>
          <div>
            <small>
              {demo?.clinic} · {demo?.ward}
            </small>
            <h1>Careflow</h1>
          </div>
        </div>
        <nav className="nav">
          <button className={view === "ward" ? "active" : ""} onClick={() => setView("ward")}>
            Station
          </button>
          <button className={view === "lab" ? "active" : ""} onClick={() => setView("lab")}>
            Labor
          </button>
          <button
            className={view === "interop" ? "active" : ""}
            onClick={() => {
              setView("interop");
              setStep(6);
            }}
          >
            HL7 / FHIR
          </button>
        </nav>
        <div className="who">
          <strong>{staff.displayName}</strong>
          <span>{staff.title}</span>
          <div className="roles">
            {ROLES.map((role) => (
              <button
                key={role.username}
                className={staff.username === role.username ? "primary" : "ghost"}
                onClick={() => void enter(role.username)}
              >
                {role.label}
              </button>
            ))}
            <button className="ghost" onClick={() => void api.logout().then(() => setStaff(null))}>
              Abmelden
            </button>
          </div>
        </div>
      </header>
      <div className="shell">
        <main>
          <div className="demo-strip">
            <span className="kicker">5-Minuten-Demo</span>
            <strong>
              Schritt {Math.min(step + 1, Math.max(steps.length, 1))} / {Math.max(steps.length, 6)}
            </strong>
            <span>{hint}</span>
          </div>
          {flash && <p className="flash">{flash}</p>}
          {illegalFlash && <p className="flash flash-warn">{illegalFlash}</p>}
          {view === "ward" && (
            <>
              <section className="stats">
                <article className="stat">
                  <strong>{ward.length}</strong>
                  <span>Fälle Station Innere 3</span>
                </article>
                <article className="stat">
                  <strong>{ward.reduce((sum, card) => sum + card.openLabs, 0)}</strong>
                  <span>offene Laboraufträge</span>
                </article>
                <article className="stat">
                  <strong>{ward.filter((card) => card.allergies.length).length}</strong>
                  <span>mit dokumentierter Allergie</span>
                </article>
                <article className="stat">
                  <strong>{ward.filter((card) => card.criticalResult).length}</strong>
                  <span>pathologische Befunde</span>
                </article>
              </section>
              <section className="beds">
                {ward.map((card) => (
                  <article key={card.id} className={card.demoStar ? "bed star" : "bed"} onClick={() => void openPatient(card.id)}>
                    <header>
                      <div>
                        <div className="kicker">
                          Bett {card.bed} · {card.mrn}
                        </div>
                        <strong>{card.displayName}</strong>
                      </div>
                      <div className="chips">
                        {card.demoStar && <span className="chip star">Demo-Fall</span>}
                        <span className={`chip ${card.acuity === "hoch" ? "high" : ""}`}>Akuität {card.acuity}</span>
                      </div>
                    </header>
                    <div>{card.workingDiagnosis}</div>
                    <div className="muted">{card.chiefComplaint}</div>
                    <div className="chips">
                      {card.allergies.map((allergy) => (
                        <span key={allergy} className="chip high">
                          {allergy}
                        </span>
                      ))}
                      {card.openLabs > 0 && <span className="chip warn">{card.openLabs} Labor offen</span>}
                      {card.criticalResult && <span className="chip high">Befund pathologisch</span>}
                    </div>
                  </article>
                ))}
              </section>
            </>
          )}

          {view === "patient" && patient && (
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
                {staff.role === "NURSE" && (
                  <p className="nurse-cpoe">Pflege hat kein CPOE (nur Lesen).</p>
                )}
                {staff.role !== "PHYSICIAN" && patient.demoStar && step >= 4 && (
                  <div className="row" style={{ marginTop: 12 }}>
                    <button className="primary" onClick={() => void enter("weber")}>
                      Als Ärztin weiter (AMTS)
                    </button>
                  </div>
                )}
                {staff.role === "PHYSICIAN" && patient.demoStar && step === 3 && (
                  <div className="row" style={{ marginTop: 12 }}>
                    <button className="primary" onClick={() => void enter("hoffmann")}>
                      Rolle Labor — Befund freigeben
                    </button>
                  </div>
                )}
              </section>
              {labOverlap && (
                <section className="alert overlap">
                  <div className="kicker">HTTP 409</div>
                  <strong>Überlappendes Laborpanel</strong>
                  <p>
                    Ein offener Laborauftrag deckt dieselbe Messung bereits ab (BBCRP umfasst Blutbild und CRP). Der
                    zweite Auftrag wird nicht angenommen.
                  </p>
                </section>
              )}
              {optimisticLock && (
                <section className="alert lock">
                  <div className="kicker">HTTP 409 · Optimistic Lock</div>
                  <strong>Auftrag wurde parallel geändert</strong>
                  <p>Auftrag wurde parallel geändert, bitte neu laden.</p>
                  <div className="row">
                    <button
                      className="primary"
                      onClick={() => {
                        setOptimisticLock(false);
                        void refreshContext();
                      }}
                    >
                      Akte neu laden
                    </button>
                  </div>
                </section>
              )}
              {cds && (
                <section className="alert">
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
                    <button className="primary" onClick={() => void orderCefu()} disabled={busy || staff.role !== "PHYSICIAN"}>
                      Stattdessen Cefuroxim (J01D)
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
                  <h3>Laborauftrag (CPOE)</h3>
                  <p className="muted">
                    Arzt löst ORM^O01 aus; das Labor antwortet mit ACK und später ORU^R01. Offene Doppelaufträge und
                    überlappende Panels (BBCRP ⊃ BB/CRP) enden mit HTTP 409.
                  </p>
                  <div className="row">
                    {catalog?.labs.map((item) => (
                      <button
                        key={item.code}
                        className={item.code === "BBCRP" ? "primary" : "ghost"}
                        disabled={busy || staff.role !== "PHYSICIAN"}
                        onClick={() => void placeLab(item.code)}
                      >
                        {item.display}
                      </button>
                    ))}
                  </div>
                  <h3>Verordnung (AMTS)</h3>
                  <p className="muted">Allergie-Match gegen Penicillin (ATC J01C) ist eine harte AMTS-Sperre (HTTP 409).</p>
                  <div className="row">
                    <button className="danger" disabled={busy || staff.role !== "PHYSICIAN"} onClick={() => void tryAmox()}>
                      Amoxicillin — Allergie-Check
                    </button>
                    {catalog?.meds
                      .filter((item) => item.code !== "AMOX")
                      .map((item) => (
                        <button
                          key={item.code}
                          className="ghost"
                          disabled={busy || staff.role !== "PHYSICIAN"}
                          onClick={() => (item.code === "CEFU" ? void orderCefu() : void placeOtherMed(item.code))}
                        >
                          {item.display}
                        </button>
                      ))}
                  </div>
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
                            order.status === "BLOCKED"
                              ? "blocked-row"
                              : order.status === "CANCELLED"
                                ? "cancelled-row"
                                : ""
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
                            {staff.role === "PHYSICIAN" && isCancellable(order) && (
                              <button
                                className="ghost"
                                disabled={busy}
                                title="Auftrag stornieren"
                                onClick={() => void cancelOrder(order.id)}
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
          )}

          {view === "lab" && (
            <section className="card">
              <h2>Labor-Worklist</h2>
              <p className="muted">Annahme: Status in Analytik. Freigabe schreibt Messwerte (LOINC) und ORU^R01.</p>
              {worklist.length === 0 && <p className="muted">Keine offenen Laboraufträge.</p>}
              <table>
                <thead>
                  <tr>
                    <th>Patient</th>
                    <th>Auftrag</th>
                    <th>Status</th>
                    <th></th>
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
                      <td className="row">
                        {item.status === "PLACED" && (
                          <button
                            className="ghost"
                            disabled={staff.role === "NURSE" || busy}
                            onClick={() => void api.acceptLab(item.orderId).then(refreshContext)}
                          >
                            Annehmen
                          </button>
                        )}
                        <button
                          className="primary"
                          disabled={staff.role === "NURSE" || busy}
                          onClick={() =>
                            item.demoStar ? void releaseDemo() : void api.releaseLab(item.orderId).then(refreshContext)
                          }
                        >
                          Befund freigeben
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          {view === "interop" && (
            <>
              <p className="muted">Audit nach CPOE, AMTS und Labor — jede Aktion im Protokoll darunter.</p>
              <section className="split">
              <article className="card">
                <h2>HL7 v2 (ORM / ORU / ACK)</h2>
                <table>
                  <thead>
                    <tr>
                      <th>Zeit</th>
                      <th>Richtung</th>
                      <th>Typ</th>
                    </tr>
                  </thead>
                  <tbody>
                    {messages.map((message) => (
                      <tr
                        key={message.id}
                        className={liveHl7?.id === message.id ? "demo-row" : ""}
                        onClick={() => setSelectedHl7(message.id)}
                        style={{ cursor: "pointer" }}
                      >
                        <td>{new Date(message.createdAt).toLocaleTimeString("de-DE")}</td>
                        <td>{message.direction === "OUTBOUND" ? "Ausgang" : "Eingang"}</td>
                        <td>
                          {message.messageType} {message.ackCode ?? ""}
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
                  {patient && (
                    <button className="primary" onClick={() => void api.fhir(patient.id).then(setFhir)}>
                      FHIR-Bundle laden
                    </button>
                  )}
                  <a
                    className="ghost"
                    href="/fhir/Patient"
                    target="_blank"
                    rel="noreferrer"
                    style={{ padding: "8px 12px", textDecoration: "none", color: "inherit", border: "1px solid var(--line)", borderRadius: 8 }}
                  >
                    /fhir/Patient
                  </a>
                </div>
                <pre>{fhir || "Akte öffnen, dann FHIR-Bundle laden."}</pre>
              </article>
            </section>
              <AuditLog events={audit} />
            </>
          )}
        </main>
        <DemoGuide steps={steps} step={step} hint={hint} />
      </div>
    </div>
  );
}
