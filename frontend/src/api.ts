import type {
  ApiErrorBody,
  AuditEvent,
  Catalog,
  CdsAlertView,
  DemoInfo,
  Hl7View,
  OrderView,
  PatientChart,
  Staff,
  WardCard,
  WorklistItem,
} from "./types";

export type CareflowRequestError = Error & { payload?: ApiErrorBody; status: number };

export function asApiError(error: unknown): {
  status: number;
  code: string;
  message: string;
  alerts: CdsAlertView[];
} {
  const err = error as CareflowRequestError;
  const payload = err.payload;
  return {
    status: err.status ?? 0,
    code: payload?.error ?? "",
    message: payload?.message ?? payload?.detail ?? err.message ?? "Unbekannter Fehler",
    alerts: payload?.alerts ?? [],
  };
}

export function isAmtsBlock(error: unknown): boolean {
  return asApiError(error).code === "CDS_BLOCK";
}

/** HTTP 409: another session changed the same order (@Version). Not AMTS, not lab-panel overlap. */
export function isOptimisticLock(error: unknown): boolean {
  const { status, code } = asApiError(error);
  return status === 409 && code === "OPTIMISTIC_LOCK";
}

/** HTTP 422: CPOE state machine rejected the transition. */
export function isIllegalState(error: unknown): boolean {
  const { status, code } = asApiError(error);
  return status === 422 || code === "ILLEGAL_STATE";
}

/** HTTP 409 on lab CPOE: open order already covers the same analytes (BBCRP ⊃ BB/CRP). */
export function isLabOverlap(error: unknown): boolean {
  const { status, code } = asApiError(error);
  return status === 409 && code !== "CDS_BLOCK" && code !== "OPTIMISTIC_LOCK";
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
    ...init,
  });
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const body = data as ApiErrorBody | null;
    const error = new Error(body?.message ?? body?.detail ?? response.statusText) as CareflowRequestError;
    error.payload = body ?? undefined;
    error.status = response.status;
    throw error;
  }
  return data as T;
}

export const api = {
  login: (username: string, password = "demo") =>
    request<Staff>("/api/auth/login", { method: "POST", body: JSON.stringify({ username, password }) }),
  logout: () => request<void>("/api/auth/logout", { method: "POST" }),
  me: () => request<Staff>("/api/auth/me"),
  demo: () => request<DemoInfo>("/api/demo"),
  catalog: () => request<Catalog>("/api/catalog"),
  ward: () => request<WardCard[]>("/api/ward"),
  patient: (id: string) => request<PatientChart>(`/api/patients/${id}`),
  placeLab: (patientId: string, code: string) =>
    request<OrderView>(`/api/patients/${patientId}/orders/lab`, {
      method: "POST",
      body: JSON.stringify({ code }),
    }),
  placeMed: (patientId: string, code: string, override = false) =>
    request<OrderView>(`/api/patients/${patientId}/orders/medication`, {
      method: "POST",
      body: JSON.stringify({ code, override }),
    }),
  cancel: (orderId: string) => request<OrderView>(`/api/orders/${orderId}/cancel`, { method: "POST" }),
  worklist: () => request<WorklistItem[]>("/api/lab/worklist"),
  acceptLab: (id: string) => request<OrderView>(`/api/lab/orders/${id}/accept`, { method: "POST" }),
  releaseLab: (id: string) => request<OrderView>(`/api/lab/orders/${id}/release`, { method: "POST" }),
  messages: () => request<Hl7View[]>("/api/interop/messages"),
  fhir: (id: string) => fetch(`/api/patients/${id}/fhir`, { credentials: "include" }).then((r) => r.text()),
  audit: () => request<AuditEvent[]>("/api/audit"),
};
