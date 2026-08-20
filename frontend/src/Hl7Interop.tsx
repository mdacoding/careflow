const ORC_LABEL: Record<string, string> = {
  NW: "Auftrag neu",
  SC: "in Analytik",
  CA: "storniert",
  CM: "befundet (ORU)",
};

const ORC_CHIP: Record<string, string> = {
  NW: "status-PLACED",
  SC: "status-IN_LAB",
  CA: "status-CANCELLED",
  CM: "status-RESULTED",
};

/** First `ORC|XX` in the raw HL7 pipe message. ACK has no ORC segment. */
function parseOrcControl(raw: string): string | undefined {
  return /ORC\|([A-Z]{2})/.exec(raw)?.[1];
}

function hdNamespace(field: string | undefined): string | undefined {
  const ns = field?.split("^")[0]?.trim();
  return ns || undefined;
}

/**
 * MSH-3 (sending app) and MSH-5 (receiving app).
 * Typical: `MSH|^~\&|CAREFLOW|fac|LABSYS|…`. HAPI encode may keep `|^~\\&|`.
 */
export function parseMshApps(raw: string): { sending?: string; receiving?: string } {
  const msh = raw.split(/\r\n|\n|\r/).find((seg) => seg.startsWith("MSH"));
  if (!msh || msh.length < 4) {
    return {};
  }
  const sep = msh[3];
  const fields = msh.split(sep);
  return {
    sending: hdNamespace(fields[2]),
    receiving: hdNamespace(fields[4]),
  };
}

export function mshRouteLabel(raw: string): string | undefined {
  const { sending, receiving } = parseMshApps(raw);
  if (!sending || !receiving) {
    return undefined;
  }
  return `${sending} → ${receiving}`;
}

export function OrcChip({ raw }: { raw: string }) {
  const code = parseOrcControl(raw);
  const label = code ? ORC_LABEL[code] : undefined;
  if (!code || !label) {
    return null;
  }
  return <span className={`chip ${ORC_CHIP[code] ?? ""}`}>{label}</span>;
}

export function MshRoute({ raw }: { raw: string }) {
  const label = mshRouteLabel(raw);
  if (!label) {
    return null;
  }
  return <div className="msh-route">{label}</div>;
}
