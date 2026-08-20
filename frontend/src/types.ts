export type Role = "PHYSICIAN" | "LAB" | "NURSE";

export interface Staff {
  username: string;
  displayName: string;
  role: Role;
  title: string;
}

export interface WardCard {
  id: string;
  mrn: string;
  displayName: string;
  birthDate: string;
  sex: string;
  bed: string;
  chiefComplaint: string;
  workingDiagnosis: string;
  acuity: string;
  demoStar: boolean;
  openLabs: number;
  criticalResult: boolean;
  allergies: string[];
}

export interface ObservationView {
  loinc: string;
  code: string;
  display: string;
  value: string | null;
  unit: string | null;
  interpretation: string | null;
  refLow: string | null;
  refHigh: string | null;
}

export interface Hl7View {
  id: string;
  orderId: string | null;
  direction: string;
  messageType: string;
  controlId: string | null;
  ackCode: string | null;
  raw: string;
  createdAt: string;
}

export interface OrderView {
  id: string;
  patientId: string;
  kind: string;
  catalogCode: string;
  displayName: string;
  status: string;
  orderedBy: string | null;
  orderedAt: string | null;
  dose: string | null;
  route: string | null;
  atc: string | null;
  pzn: string | null;
  blocked: boolean;
  hl7ControlId: string | null;
  observations: ObservationView[];
  hl7: Hl7View[];
}

export interface PatientChart {
  id: string;
  mrn: string;
  givenName: string;
  familyName: string;
  birthDate: string;
  sex: string;
  ward: string;
  bed: string;
  chiefComplaint: string;
  workingDiagnosis: string;
  demoStar: boolean;
  acuity: string;
  encounterId: string;
  admittedAt: string;
  allergies: { substance: string; atcPrefix: string | null; criticality: string }[];
  orders: OrderView[];
  alerts: { id: string; severity: string; ruleId: string; title: string; message: string; overridden: boolean }[];
}

export interface WorklistItem {
  orderId: string;
  patientId: string;
  patientName: string;
  mrn: string;
  bed: string;
  catalogCode: string;
  displayName: string;
  status: string;
  orderedAt: string;
  demoStar: boolean;
}

export interface Catalog {
  labs: { code: string; display: string; loincPanel: string; description: string }[];
  meds: { code: string; display: string; atc: string; pzn: string; dose: string; route: string }[];
}

export interface DemoInfo {
  clinic: string;
  ward: string;
  starPatientId: string;
  labPreset: string;
  blockMed: string;
  safeMed: string;
  steps: string[];
}

export interface CdsError {
  error: string;
  message: string;
  alerts: { ruleId: string; severity: string; title: string; message: string }[];
}
