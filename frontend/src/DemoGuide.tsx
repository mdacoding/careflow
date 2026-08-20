const FALLBACK_STEPS = [
  "Als Ärztin anmelden, Stationsboard Innere 3",
  "Elena Krüger öffnen (Demo-Fall, Allergie Penicillin)",
  "Laborauftrag Blutbild + CRP → HL7 ORM^O01",
  "Labor: Auftrag annehmen, Befund freigeben → ORU^R01",
  "CRP pathologisch; Amoxicillin — AMTS sperrt (ATC J01C)",
  "Cefuroxim mit Kreuzallergie-Hinweis; Interop: HL7 und FHIR-Bundle",
];

export function demoHint(step: number, view: string, role: string, overlapVisible: boolean): string {
  if (overlapVisible) {
    return "HTTP 409: überlappendes Laborpanel. Als Nächstes Rolle Labor — Befund freigeben.";
  }
  if (step <= 1 && view !== "patient") {
    return "Elena Krüger (Demo-Fall, Bett 12) auf dem Stationsboard öffnen.";
  }
  if (step <= 2 && view === "patient") {
    return "Blutbild + CRP übermitteln. Ein zweites Panel derselben Messung → HTTP 409, überlappendes Laborpanel.";
  }
  if (step === 3 && view !== "lab") {
    return "Rolle Labor wählen — Auftrag annehmen und Befund freigeben (ORU^R01).";
  }
  if (step === 3 && view === "lab") {
    return "Beim Demo-Fall Befund freigeben. CRP wird pathologisch (HH).";
  }
  if (step === 4 && role !== "PHYSICIAN") {
    return "Als Ärztin in die Akte zurück — als Nächstes Amoxicillin (AMTS-Sperre).";
  }
  if (step === 4) {
    return "Amoxicillin anstoßen: HTTP 409 AMTS-Sperre wegen Allergie Penicillin (ATC J01C).";
  }
  if (step === 5) {
    return "Cefuroxim verordnen (Hinweis Kreuzallergie β-Laktam), danach HL7 / FHIR öffnen.";
  }
  if (step >= 6) {
    return "Interop-Log: ORM^O01, ACK, ORU^R01 und FHIR-Bundle derselben Akte.";
  }
  return "Stationsboard Innere 3 — Demo-Fall Elena Krüger.";
}

export function DemoGuide({
  steps,
  step,
  hint,
}: {
  steps: string[];
  step: number;
  hint: string;
}) {
  const list = steps.length ? steps : FALLBACK_STEPS;
  const current = Math.min(step, Math.max(list.length - 1, 0));
  return (
    <aside className="guide">
      <div className="kicker">Vorführung</div>
      <h2>5-Minuten-Demo</h2>
      <ol>
        {list.map((text, index) => (
          <li
            key={text}
            className={index === current && step < list.length ? "current" : index < step ? "done" : ""}
          >
            {text}
          </li>
        ))}
      </ol>
      {hint && (
        <div className="next-action">
          <div className="kicker">Jetzt</div>
          <p>{hint}</p>
        </div>
      )}
      <p className="muted" style={{ marginTop: 16 }}>
        Synthetische Demodaten, fiktives Musterklinikum Nord. Fall: Elena Krüger, Allergie Penicillin, Verdacht
        Pneumonie.
      </p>
      <p className="muted">
        Optionaler zweiter Fall: Karl-Heinz Vogt (NSAR / CKD-EPI) — nicht der 5-Minuten-Pfad.
      </p>
    </aside>
  );
}
