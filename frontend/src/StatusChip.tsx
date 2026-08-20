export function statusLabel(status: string) {
  return (
    {
      PLACED: "übermittelt",
      IN_LAB: "in Analytik",
      RESULTED: "befundet",
      ACTIVE: "aktiv",
      BLOCKED: "AMTS-Sperre",
      CANCELLED: "storniert",
    }[status] ?? status
  );
}

export function interpLabel(code: string | null) {
  if (!code) {
    return "";
  }
  return (
    {
      N: "Normbereich",
      H: "erhöht",
      HH: "kritisch erhöht",
      L: "erniedrigt",
      LL: "kritisch erniedrigt",
    }[code] ?? code
  );
}

export function StatusChip({ status }: { status: string }) {
  return <span className={`chip status-${status}`}>{statusLabel(status)}</span>;
}
