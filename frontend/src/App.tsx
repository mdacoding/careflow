import { useEffect, useMemo, useState } from "react";
import { api } from "./api";
import type {
  Catalog,
  CdsError,
  DemoInfo,
  Hl7View,
  PatientChart,
  Staff,
  WardCard,
  WorklistItem,
} from "./types";

type View = "ward" | "patient" | "lab" | "interop";

const ROLES: { username: string; label: string }[] = [
  { username: "weber", label: "Ärztin" },
  { username: "hoffmann", label: "Labor" },
  { username: "schmidt", label: "Pflege" },
];

function statusLabel(status: string) {
  return (
    {
      PLACED: "im Labor eingegangen",
      IN_LAB: "in Bearbeitung",
      RESULTED: "befundet",
      ACTIVE: "aktiv",
      BLOCKED: "gesperrt",
      CANCELLED: "storniert",
    }[status] ?? status
  );
}

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
  const [fhir, setFhir] = useState("");
  const [selectedHl7, setSelectedHl7] = useState<string>("");
  const [flash, setFlash] = useState("");
  const [cds, setCds] = useState<CdsError | null>(null);
  const [busy, setBusy] = useState(false);

  async function refreshWard() {
    setWard(await api.ward());
  }

  async function openPatient(id: string) {
    const chart = await api.patient(id);
    setPatient(chart);
    setView("patient");
    setCds(null);
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

  async function enter(username: string, startDemo = false) {
    const session = await api.login(username);
    setStaff(session);
    setDemo(await api.demo());
    setView(username === "hoffmann" ? "lab" : "ward");
    setStep(startDemo ? 1 : 0);
    setFlash(`Angemeldet als ${session.displayName}`);
  }

  async function placeDemoLab() {
    if (!demo || !patient) {
      return;
    }
    setBusy(true);
    try {
      await api.placeLab(patient.id, demo.labPreset);
      setFlash("Laborauftrag gesendet — HL7 ORM^O01 liegt im Interop-Log.");
      setStep(3);
      await refreshContext();
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
      setFlash("Befund freigegeben — ORU^R01 erzeugt.");
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
      const payload = (error as { payload?: CdsError }).payload;
      if (payload?.error === "CDS_BLOCK") {
        setCds(payload);
        setFlash("AMTS hat Amoxicillin gesperrt.");
        setStep(5);
      } else {
        setFlash((error as Error).message);
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
      setFlash("Cefuroxim verordnet — Kreuzallergie nur als Hinweis.");
      setStep(6);
      await refreshContext();
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
            Klinischer Stationsarbeitsplatz mit Auftragswesen, Laborbefund, AMTS und Interop (HL7 v2 / FHIR R4).
            Nur synthetische Demodaten, Passwort überall <code>demo</code>.
          </p>
          <div className="login-grid">
            <button className="staff" onClick={() => void enter("weber", true)}>
              <span className="kicker">5-Minuten-Demo</span>
              <b>Dr. med. Lena Weber</b>
              <span className="muted">Oberärztin — startet den geführten Fall Elena Krüger</span>
            </button>
            <button className="staff" onClick={() => void enter("hoffmann")}>
              <span className="kicker">Labor</span>
              <b>Tim Hoffmann</b>
              <span className="muted">MTA — Worklist und Befundfreigabe</span>
            </button>
            <button className="staff" onClick={() => void enter("schmidt")}>
              <span className="kicker">Pflege</span>
              <b>Paula Schmidt</b>
              <span className="muted">Lesen, keine Verordnung</span>
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="logo">Cf</div>
          <div>
            <small>{demo?.clinic} · {demo?.ward}</small>
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
          <button className={view === "interop" ? "active" : ""} onClick={() => { setView("interop"); setStep(6); }}>
            Interop
          </button>
        </nav>
        <div className="who">
          <strong>{staff.displayName}</strong>
          <span>{staff.title}</span>
          <div className="roles">
            {ROLES.map((role) => (
              <button key={role.username} className={staff.username === role.username ? "primary" : "ghost"} onClick={() => void enter(role.username)}>
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
          <p className="flash">{flash}</p>
          {view === "ward" && (
            <>
              <section className="stats">
                <article className="stat"><strong>{ward.length}</strong><span>Fälle auf Station</span></article>
                <article className="stat"><strong>{ward.reduce((sum, card) => sum + card.openLabs, 0)}</strong><span>offene Laboraufträge</span></article>
                <article className="stat"><strong>{ward.filter((card) => card.allergies.length).length}</strong><span>mit Allergie</span></article>
                <article className="stat"><strong>{ward.filter((card) => card.criticalResult).length}</strong><span>pathologische Befunde</span></article>
              </section>
              <section className="beds">
                {ward.map((card) => (
                  <article
                    key={card.id}
                    className={card.demoStar ? "bed star" : "bed"}
                    onClick={() => void openPatient(card.id)}
                  >
                    <header>
                      <div>
                        <div className="kicker">Bett {card.bed} · {card.mrn}</div>
                        <strong>{card.displayName}</strong>
                      </div>
                      <div className="chips">
                        {card.demoStar && <span className="chip star">Demo-Fall</span>}
                        <span className={`chip ${card.acuity === "hoch" ? "high" : ""}`}>{card.acuity}</span>
                      </div>
                    </header>
                    <div>{card.workingDiagnosis}</div>
                    <div className="muted">{card.chiefComplaint}</div>
                    <div className="chips">
                      {card.allergies.map((allergy) => (
                        <span key={allergy} className="chip high">{allergy}</span>
                      ))}
                      {card.openLabs > 0 && <span className="chip warn">{card.openLabs} Labor offen</span>}
                      {card.criticalResult && <span className="chip high">pathol. Befund</span>}
                    </div>
                  </article>
                ))}
              </section>
            </>
          )}

          {view === "patient" && patient && (
            <>
              <section className="card">
                <div className="kicker">{patient.mrn} · {patient.ward} Bett {patient.bed}</div>
                <h2 style={{ margin: "4px 0 8px" }}>{patient.familyName}, {patient.givenName}</h2>
                <p className="muted">
                  * {patient.birthDate} · {patient.sex === "F" ? "weiblich" : "männlich"} · Aufnahme {new Date(patient.admittedAt).toLocaleString("de-DE")}
                </p>
                <p><strong>{patient.workingDiagnosis}</strong> — {patient.chiefComplaint}</p>
                <div className="chips">
                  {patient.allergies.map((allergy) => (
                    <span key={allergy.substance} className="chip high">Allergie {allergy.substance} ({allergy.atcPrefix})</span>
                  ))}
                </div>
              </section>
              {cds && (
                <section className="alert">
                  <strong>{cds.alerts[0]?.title ?? "AMTS-Sperre"}</strong>
                  <p>{cds.alerts[0]?.message}</p>
                  <div className="row">
                    <button className="primary" onClick={() => void orderCefu()} disabled={busy || staff.role !== "PHYSICIAN"}>
                      Stattdessen Cefuroxim
                    </button>
                  </div>
                </section>
              )}
              <section className="split">
                <article className="card">
                  <h3>Labor anordnen</h3>
                  <p className="muted">Rolle Arzt erzeugt ORM^O01, das Labor antwortet mit ACK und später ORU^R01.</p>
                  <div className="row">
                    {catalog?.labs.map((item) => (
                      <button
                        key={item.code}
                        className={item.code === "BBCRP" ? "primary" : "ghost"}
                        disabled={busy || staff.role !== "PHYSICIAN"}
                        onClick={() => item.code === "BBCRP" ? void placeDemoLab() : void api.placeLab(patient.id, item.code).then(() => refreshContext())}
                      >
                        {item.display}
                      </button>
                    ))}
                  </div>
                  <h3>Medikation</h3>
                  <div className="row">
                    <button className="danger" disabled={busy || staff.role !== "PHYSICIAN"} onClick={() => void tryAmox()}>
                      Amoxicillin (Allergie-Demo)
                    </button>
                    {catalog?.meds.filter((item) => item.code !== "AMOX").map((item) => (
                      <button
                        key={item.code}
                        className="ghost"
                        disabled={busy || staff.role !== "PHYSICIAN"}
                        onClick={() => void api.placeMed(patient.id, item.code).then(() => refreshContext()).catch((error) => setFlash(error.message))}
                      >
                        {item.display}
                      </button>
                    ))}
                  </div>
                </article>
                <article className="card">
                  <h3>Aufträge & Befunde</h3>
                  <table>
                    <thead>
                      <tr><th>Auftrag</th><th>Status</th><th>Werte</th></tr>
                    </thead>
                    <tbody>
                      {patient.orders.map((order) => (
                        <tr key={order.id}>
                          <td>
                            {order.displayName}
                            <div className="muted">{order.kind} {order.atc ?? ""}</div>
                          </td>
                          <td><span className={`chip ${order.status}`}>{statusLabel(order.status)}</span></td>
                          <td>
                            {order.observations.map((obs) => (
                              <div key={obs.code} className={obs.interpretation === "N" ? "" : "chip high"} style={{ display: "block", marginBottom: 4 }}>
                                {obs.display} {obs.value} {obs.unit} ({obs.interpretation})
                              </div>
                            ))}
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
              <p className="muted">Annahme setzt den Status auf in Bearbeitung, Freigabe erzeugt Messwerte und ORU^R01.</p>
              <table>
                <thead>
                  <tr><th>Patient</th><th>Auftrag</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  {worklist.map((item) => (
                    <tr key={item.orderId}>
                      <td>
                        {item.patientName} · Bett {item.bed}
                        {item.demoStar && <div className="chip star">Demo</div>}
                      </td>
                      <td>{item.displayName}</td>
                      <td><span className={`chip ${item.status}`}>{statusLabel(item.status)}</span></td>
                      <td className="row">
                        {item.status === "PLACED" && (
                          <button className="ghost" disabled={staff.role === "NURSE" || busy} onClick={() => void api.acceptLab(item.orderId).then(refreshContext)}>
                            Annehmen
                          </button>
                        )}
                        <button className="primary" disabled={staff.role === "NURSE" || busy} onClick={() => item.demoStar ? void releaseDemo() : void api.releaseLab(item.orderId).then(refreshContext)}>
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
            <section className="split">
              <article className="card">
                <h2>HL7 v2 Nachrichten</h2>
                <table>
                  <thead>
                    <tr><th>Zeit</th><th>Richtung</th><th>Typ</th></tr>
                  </thead>
                  <tbody>
                    {messages.map((message) => (
                      <tr key={message.id} onClick={() => setSelectedHl7(message.id)} style={{ cursor: "pointer" }}>
                        <td>{new Date(message.createdAt).toLocaleTimeString("de-DE")}</td>
                        <td>{message.direction}</td>
                        <td>{message.messageType} {message.ackCode ?? ""}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <pre>{liveHl7?.raw ?? "Noch keine Nachrichten."}</pre>
              </article>
              <article className="card">
                <h2>FHIR R4 Bundle</h2>
                <p className="muted">
                  Projektion der Akte als Collection-Bundle. Zusätzlich HAPI-Endpunkt <code>/fhir/Patient</code>.
                </p>
                <div className="row">
                  {patient && (
                    <button className="primary" onClick={() => void api.fhir(patient.id).then(setFhir)}>
                      Bundle dieser Akte
                    </button>
                  )}
                  <a className="ghost" href="/fhir/Patient" target="_blank" rel="noreferrer" style={{ padding: "8px 12px", textDecoration: "none", color: "inherit", border: "1px solid var(--line)", borderRadius: 8 }}>
                    /fhir/Patient
                  </a>
                </div>
                <pre>{fhir || "Akte öffnen, dann Bundle laden."}</pre>
              </article>
            </section>
          )}
        </main>
        <aside className="guide">
          <div className="kicker">Vorführung</div>
          <h2>5-Minuten-Demo</h2>
          <ol>
            {(demo?.steps ?? []).map((text, index) => (
              <li key={text} className={index === step ? "current" : index < step ? "done" : ""}>
                {text}
              </li>
            ))}
          </ol>
          <p className="muted" style={{ marginTop: 16 }}>
            Keine echten Patientendaten, kein Klinikname eines Arbeitgebers. Fallakte: Elena Krüger, Penicillin-Allergie, Pneumonie-Verdacht.
          </p>
        </aside>
      </div>
    </div>
  );
}
