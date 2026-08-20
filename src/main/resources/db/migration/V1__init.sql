CREATE TABLE patients (
    id VARCHAR(36) PRIMARY KEY,
    mrn VARCHAR(32) NOT NULL UNIQUE,
    given_name VARCHAR(80) NOT NULL,
    family_name VARCHAR(80) NOT NULL,
    birth_date DATE NOT NULL,
    sex VARCHAR(1) NOT NULL,
    ward VARCHAR(40) NOT NULL,
    bed VARCHAR(10) NOT NULL,
    department VARCHAR(80) NOT NULL,
    chief_complaint VARCHAR(200),
    working_diagnosis VARCHAR(200),
    demo_star BOOLEAN NOT NULL DEFAULT FALSE,
    acuity VARCHAR(20) NOT NULL
);

CREATE TABLE allergies (
    id VARCHAR(36) PRIMARY KEY,
    patient_id VARCHAR(36) NOT NULL REFERENCES patients (id),
    substance VARCHAR(80) NOT NULL,
    atc_prefix VARCHAR(20),
    snomed VARCHAR(40),
    criticality VARCHAR(20) NOT NULL
);

CREATE TABLE encounters (
    id VARCHAR(36) PRIMARY KEY,
    patient_id VARCHAR(36) NOT NULL REFERENCES patients (id),
    status VARCHAR(20) NOT NULL,
    admitted_at TIMESTAMP NOT NULL,
    department VARCHAR(80) NOT NULL
);

CREATE TABLE clinical_orders (
    id VARCHAR(36) PRIMARY KEY,
    patient_id VARCHAR(36) NOT NULL REFERENCES patients (id),
    encounter_id VARCHAR(36) NOT NULL REFERENCES encounters (id),
    kind VARCHAR(20) NOT NULL,
    catalog_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ordered_by VARCHAR(80),
    ordered_at TIMESTAMP,
    accepted_at TIMESTAMP,
    completed_at TIMESTAMP,
    placer_number VARCHAR(40),
    hl7_control_id VARCHAR(40),
    dose VARCHAR(80),
    route VARCHAR(40),
    atc VARCHAR(20),
    pzn VARCHAR(20),
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(500)
);

CREATE INDEX idx_orders_patient ON clinical_orders (patient_id);
CREATE INDEX idx_orders_status ON clinical_orders (status);

CREATE TABLE observations (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES clinical_orders (id),
    loinc VARCHAR(20) NOT NULL,
    code VARCHAR(40) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    value_num DECIMAL(12, 3),
    value_text VARCHAR(80),
    unit VARCHAR(40),
    interpretation VARCHAR(8),
    ref_low DECIMAL(12, 3),
    ref_high DECIMAL(12, 3),
    sort_order INT NOT NULL
);

CREATE TABLE cds_alerts (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) REFERENCES clinical_orders (id),
    patient_id VARCHAR(36) NOT NULL REFERENCES patients (id),
    severity VARCHAR(20) NOT NULL,
    rule_id VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(600) NOT NULL,
    overridden BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE hl7_messages (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) REFERENCES clinical_orders (id),
    direction VARCHAR(20) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    control_id VARCHAR(40),
    ack_code VARCHAR(10),
    raw_message VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_hl7_order ON hl7_messages (order_id);

CREATE TABLE audit_events (
    id VARCHAR(36) PRIMARY KEY,
    actor VARCHAR(80) NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40),
    entity_id VARCHAR(36),
    detail VARCHAR(800),
    created_at TIMESTAMP NOT NULL
);
