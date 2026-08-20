import type {
  Catalog,
  CdsError,
  DemoInfo,
  Hl7View,
  OrderView,
  PatientChart,
  Staff,
  WardCard,
  WorklistItem,
} from "./types";

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
    const error = new Error(data?.message ?? response.statusText) as Error & { payload?: CdsError; status: number };
    error.payload = data;
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
  worklist: () => request<WorklistItem[]>("/api/lab/worklist"),
  acceptLab: (id: string) => request<OrderView>(`/api/lab/orders/${id}/accept`, { method: "POST" }),
  releaseLab: (id: string) => request<OrderView>(`/api/lab/orders/${id}/release`, { method: "POST" }),
  messages: () => request<Hl7View[]>("/api/interop/messages"),
  fhir: (id: string) => fetch(`/api/patients/${id}/fhir`, { credentials: "include" }).then((r) => r.text()),
};
