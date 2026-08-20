import { useEffect, useMemo, useState } from "react";
import { api, asApiError, isAmtsBlock, isIllegalState, isLabOverlap, isOptimisticLock } from "./api";
import { DemoGuide, demoHint } from "./DemoGuide";
import { InteropView } from "./InteropView";
import { LabWorklist } from "./LabWorklist";
import { LoginScreen } from "./LoginScreen";
import { PatientView } from "./PatientView";
import { WardBoard } from "./WardBoard";
import type { AuditEvent, Catalog, CdsError, DemoInfo, Hl7View, PatientChart, Staff, WardCard, WorklistItem, WsEvent } from "./types";

const LIVE_WORDING: Record<string, string> = {
  ORDER_PLACED: "Laborauftrag übermittelt",
  ORDER_ACCEPTED: "Laborauftrag angenommen",
  RESULT_READY: "Befund freigegeben",
  MEDICATION_BLOCKED: "AMTS-Sperre",
  MEDICATION_ORDERED: "Verordnung aktiv",
  ORDER_CANCELLED: "Auftrag storniert",
};

function parseWsEvent(raw: string): WsEvent | null {
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || typeof (parsed as WsEvent).type !== "string") {
      return null;
    }
    return parsed as WsEvent;
  } catch {
    return null;
  }
}

function liveWording(event: WsEvent): string {
  return LIVE_WORDING[event.type] ?? event.message ?? event.type;
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
  const [liveEvent, setLiveEvent] = useState<WsEvent | null>(null);
  const [liveReconnecting, setLiveReconnecting] = useState(false);

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

    let cancelled = false;
    let socket: WebSocket | undefined;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let openedOnce = false;

    const connect = () => {
      if (cancelled) {
        return;
      }
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      socket = new WebSocket(`${protocol}://${window.location.host}/api/ws`);
      socket.onopen = () => {
        if (cancelled) {
          return;
        }
        openedOnce = true;
        setLiveReconnecting(false);
      };
      socket.onmessage = (event) => {
        const payload = parseWsEvent(typeof event.data === "string" ? event.data : "");
        if (payload) {
          setLiveEvent(payload);
        }
        void refreshContext();
      };
      socket.onerror = () => {
        if (cancelled || !openedOnce) {
          return;
        }
        setLiveReconnecting(true);
      };
      socket.onclose = () => {
        if (cancelled) {
          return;
        }
        if (openedOnce) {
          setLiveReconnecting(true);
        }
        reconnectTimer = setTimeout(connect, 2000);
      };
    };

    connect();

    return () => {
      cancelled = true;
      if (reconnectTimer !== undefined) {
        clearTimeout(reconnectTimer);
      }
      setLiveReconnecting(false);
      socket?.close();
    };
  }, [staff?.username]);

  const liveHl7 = useMemo(
    () => messages.find((message) => message.id === selectedHl7) ?? messages[0],
    [messages, selectedHl7],
  );

  const steps = demo?.steps ?? [];
  const hint = demoHint(step, view, staff?.role ?? "", labOverlap);

  async function enter(username: string, startDemo = false) {
    try {
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
    } catch (error) {
      const parsed = asApiError(error);
      const generic = !parsed.message || parsed.message === "Unauthorized" || parsed.message === "Forbidden";
      setFlash(generic ? "Anmeldung fehlgeschlagen" : parsed.message);
    }
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

  async function overrideAmts() {
    if (!patient) {
      return;
    }
    setBusy(true);
    try {
      await api.placeMed(patient.id, demo?.blockMed ?? "AMOX", true);
      setFlash("Verordnung als AMTS-Override dokumentiert — nicht freigegeben.");
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
      await refreshContext();
    } finally {
      setBusy(false);
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
    return <LoginScreen flash={flash} onEnter={(username, startDemo) => void enter(username, startDemo)} />;
  }

  return (
    <div className="app">
      <header>
        <div className="topbar">
          <div className="brand">
            <div className="logo">Cf</div>
            <div>
              <small>
                {demo?.clinic} · {demo?.ward}
              </small>
              <h1>Careflow</h1>
            </div>
          </div>
          <nav className="nav" aria-label="Arbeitsplatz">
            <button
              type="button"
              className={view === "ward" || view === "patient" ? "active" : ""}
              aria-current={view === "ward" || view === "patient" ? "page" : undefined}
              onClick={() => setView("ward")}
            >
              Station
            </button>
            <button
              type="button"
              className={view === "lab" ? "active" : ""}
              aria-current={view === "lab" ? "page" : undefined}
              onClick={() => setView("lab")}
            >
              Labor
            </button>
            <button
              type="button"
              className={view === "interop" ? "active" : ""}
              aria-current={view === "interop" ? "page" : undefined}
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
                  type="button"
                  className={staff.username === role.username ? "primary" : "ghost"}
                  aria-pressed={staff.username === role.username}
                  onClick={() => void enter(role.username)}
                >
                  {role.label}
                </button>
              ))}
              <button type="button" className="ghost" onClick={() => void api.logout().then(() => setStaff(null))}>
                Abmelden
              </button>
            </div>
          </div>
        </div>
        <p className="live-line" aria-live="polite">
          <span className="kicker">Live</span>
          <b>{liveReconnecting ? "Verbindung …" : liveEvent ? liveWording(liveEvent) : "—"}</b>
        </p>
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
          {flash && (
            <p className="flash" role="status" aria-live="polite">
              {flash}
            </p>
          )}
          {illegalFlash && (
            <p className="flash flash-warn" role="status" aria-live="polite">
              {illegalFlash}
            </p>
          )}
          {view === "ward" && <WardBoard ward={ward} onOpen={(id) => void openPatient(id)} />}
          {view === "patient" && patient && (
            <PatientView
              patient={patient}
              catalog={catalog}
              role={staff.role}
              step={step}
              busy={busy}
              cds={cds}
              labOverlap={labOverlap}
              optimisticLock={optimisticLock}
              onEnter={(username) => void enter(username)}
              onReload={() => {
                setOptimisticLock(false);
                void refreshContext();
              }}
              onPlaceLab={(code) => void placeLab(code)}
              onTryAmox={() => void tryAmox()}
              onOrderCefu={() => void orderCefu()}
              onPlaceOtherMed={(code) => void placeOtherMed(code)}
              onOverrideAmts={() => void overrideAmts()}
              onCancel={(orderId) => void cancelOrder(orderId)}
            />
          )}
          {view === "lab" && (
            <LabWorklist
              worklist={worklist}
              role={staff.role}
              busy={busy}
              onAccept={(orderId) => void api.acceptLab(orderId).then(refreshContext)}
              onRelease={(orderId) => void api.releaseLab(orderId).then(refreshContext)}
              onReleaseDemo={() => void releaseDemo()}
            />
          )}
          {view === "interop" && (
            <InteropView
              messages={messages}
              liveHl7={liveHl7}
              fhir={fhir}
              audit={audit}
              patientId={patient?.id}
              onSelectHl7={setSelectedHl7}
              onLoadFhir={(id) => void api.fhir(id).then(setFhir)}
            />
          )}
        </main>
        <DemoGuide steps={steps} step={step} hint={hint} />
      </div>
    </div>
  );
}
