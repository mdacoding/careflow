export function LoginScreen({
  flash,
  onEnter,
}: {
  flash: string;
  onEnter: (username: string, startDemo?: boolean) => void;
}) {
  return (
    <div className="login">
      <div className="login-card">
        <div className="kicker">Musterklinikum Nord · Innere 3</div>
        <h1>Careflow</h1>
        <p className="muted">
          Klinischer Stationsarbeitsplatz: CPOE, Befundrücklauf, AMTS, HL7 v2 ORM/ORU, FHIR R4. Synthetische Demodaten.
          Passwort überall <code>demo</code>.
        </p>
        {flash && (
          <p className="flash flash-warn" role="status" aria-live="polite">
            {flash}
          </p>
        )}
        <div className="login-grid">
          <button type="button" className="staff" onClick={() => onEnter("weber", true)}>
            <span className="kicker">5-Minuten-Demo</span>
            <b>Dr. med. Lena Weber</b>
            <span className="muted">Oberärztin — führt den Demo-Fall Elena Krüger</span>
          </button>
          <button type="button" className="staff" onClick={() => onEnter("hoffmann")}>
            <span className="kicker">Labor</span>
            <b>Tim Hoffmann</b>
            <span className="muted">MTA — Labor-Worklist, Annahme, Befundfreigabe</span>
          </button>
          <button type="button" className="staff" onClick={() => onEnter("schmidt")}>
            <span className="kicker">Pflege</span>
            <b>Paula Schmidt</b>
            <span className="muted">Station — lesend, ohne CPOE (RBAC)</span>
          </button>
        </div>
      </div>
    </div>
  );
}
