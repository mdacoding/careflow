package de.careflow.fhir;

import ca.uhn.fhir.context.FhirContext;
import de.careflow.domain.AllergyEntity;
import de.careflow.domain.AllergyRepository;
import de.careflow.domain.ClinicalOrderEntity;
import de.careflow.domain.ClinicalOrderRepository;
import de.careflow.domain.EncounterEntity;
import de.careflow.domain.EncounterRepository;
import de.careflow.domain.ObservationEntity;
import de.careflow.domain.ObservationRepository;
import de.careflow.domain.OrderKind;
import de.careflow.domain.OrderStatus;
import de.careflow.domain.PatientEntity;
import de.careflow.domain.PatientRepository;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class FhirMapper {

    public static final String PROFILE_PATIENT = "https://gematik.de/fhir/isik/StructureDefinition/ISiKPatient";
    private final PatientRepository patients;
    private final AllergyRepository allergies;
    private final EncounterRepository encounters;
    private final ClinicalOrderRepository orders;
    private final ObservationRepository observations;
    private final FhirContext fhirContext;

    public FhirMapper(
            PatientRepository patients,
            AllergyRepository allergies,
            EncounterRepository encounters,
            ClinicalOrderRepository orders,
            ObservationRepository observations,
            FhirContext fhirContext) {
        this.patients = patients;
        this.allergies = allergies;
        this.encounters = encounters;
        this.orders = orders;
        this.observations = observations;
        this.fhirContext = fhirContext;
    }

    public Patient toPatient(PatientEntity entity) {
        Patient patient = new Patient();
        patient.setId(new IdType("Patient", entity.getId()));
        patient.setMeta(new Meta().addProfile(PROFILE_PATIENT));
        patient.addIdentifier(new Identifier()
                .setSystem("https://musterklinikum.example/sid/mrn")
                .setValue(entity.getMrn()));
        patient.addName().setFamily(entity.getFamilyName()).addGiven(entity.getGivenName());
        patient.setBirthDate(Date.from(entity.getBirthDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
        patient.setGender("M".equals(entity.getSex())
                ? Enumerations.AdministrativeGender.MALE
                : Enumerations.AdministrativeGender.FEMALE);
        return patient;
    }

    public Encounter toEncounter(EncounterEntity entity, PatientEntity patient) {
        Encounter encounter = new Encounter();
        encounter.setId(new IdType("Encounter", entity.getId()));
        encounter.setStatus(Encounter.EncounterStatus.INPROGRESS);
        encounter.setClass_(new Coding("http://terminology.hl7.org/CodeSystem/v3-ActCode", "IMP", "inpatient"));
        encounter.setSubject(new Reference("Patient/" + patient.getId()));
        encounter.getPeriod().setStartElement(new DateTimeType(Date.from(entity.getAdmittedAt())));
        encounter.addLocation().getLocation().setDisplay(patient.getWard() + " / Bett " + patient.getBed());
        return encounter;
    }

    public AllergyIntolerance toAllergy(AllergyEntity entity) {
        AllergyIntolerance allergy = new AllergyIntolerance();
        allergy.setId(new IdType("AllergyIntolerance", entity.getId()));
        allergy.setPatient(new Reference("Patient/" + entity.getPatientId()));
        allergy.setClinicalStatus(new CodeableConcept().addCoding(new Coding(
                "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", "active", "Active")));
        allergy.setCode(new CodeableConcept().setText(entity.getSubstance()).addCoding(
                new Coding("http://www.whocc.no/atc", entity.getAtcPrefix(), entity.getSubstance())));
        allergy.setCriticality("high".equalsIgnoreCase(entity.getCriticality())
                ? AllergyIntolerance.AllergyIntoleranceCriticality.HIGH
                : AllergyIntolerance.AllergyIntoleranceCriticality.LOW);
        return allergy;
    }

    public ServiceRequest toServiceRequest(ClinicalOrderEntity order) {
        ServiceRequest request = new ServiceRequest();
        request.setId(new IdType("ServiceRequest", order.getId()));
        request.setStatus(switch (order.getStatus()) {
            case CANCELLED -> ServiceRequest.ServiceRequestStatus.REVOKED;
            case RESULTED -> ServiceRequest.ServiceRequestStatus.COMPLETED;
            default -> ServiceRequest.ServiceRequestStatus.ACTIVE;
        });
        request.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
        request.setSubject(new Reference("Patient/" + order.getPatientId()));
        request.setCode(new CodeableConcept().setText(order.getDisplayName())
                .addCoding(new Coding("https://musterklinikum.example/lab", order.getCatalogCode(), order.getDisplayName())));
        if (order.getOrderedAt() != null) {
            request.setAuthoredOn(Date.from(order.getOrderedAt()));
        }
        return request;
    }

    public DiagnosticReport toReport(ClinicalOrderEntity order, List<ObservationEntity> results) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId(new IdType("DiagnosticReport", order.getId()));
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        report.setSubject(new Reference("Patient/" + order.getPatientId()));
        report.setCode(new CodeableConcept().setText(order.getDisplayName()));
        for (ObservationEntity observation : results) {
            report.addResult(new Reference("Observation/" + observation.getId()));
        }
        if (order.getCompletedAt() != null) {
            report.setIssued(Date.from(order.getCompletedAt()));
        }
        return report;
    }

    public Observation toObservation(ObservationEntity entity, ClinicalOrderEntity order) {
        Observation observation = new Observation();
        observation.setId(new IdType("Observation", entity.getId()));
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setSubject(new Reference("Patient/" + order.getPatientId()));
        observation.setCode(new CodeableConcept().setText(entity.getDisplayName())
                .addCoding(new Coding("http://loinc.org", entity.getLoinc(), entity.getDisplayName())));
        if (entity.getValueNum() != null) {
            observation.setValue(new Quantity()
                    .setValue(entity.getValueNum())
                    .setUnit(entity.getUnit())
                    .setSystem("http://unitsofmeasure.org"));
        }
        if (entity.getInterpretation() != null) {
            observation.addInterpretation(new CodeableConcept().addCoding(new Coding(
                    "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                    entity.getInterpretation(),
                    entity.getInterpretation())));
        }
        if (entity.getRefLow() != null || entity.getRefHigh() != null) {
            Observation.ObservationReferenceRangeComponent range = observation.addReferenceRange();
            if (entity.getRefLow() != null) {
                range.setLow(new Quantity().setValue(entity.getRefLow()).setUnit(entity.getUnit()));
            }
            if (entity.getRefHigh() != null) {
                range.setHigh(new Quantity().setValue(entity.getRefHigh()).setUnit(entity.getUnit()));
            }
        }
        return observation;
    }

    public MedicationRequest toMedication(ClinicalOrderEntity order) {
        MedicationRequest request = new MedicationRequest();
        request.setId(new IdType("MedicationRequest", order.getId()));
        request.setStatus(switch (order.getStatus()) {
            case BLOCKED -> MedicationRequest.MedicationRequestStatus.STOPPED;
            case CANCELLED -> MedicationRequest.MedicationRequestStatus.CANCELLED;
            default -> MedicationRequest.MedicationRequestStatus.ACTIVE;
        });
        request.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        request.setSubject(new Reference("Patient/" + order.getPatientId()));
        request.setMedication(new CodeableConcept().setText(order.getDisplayName())
                .addCoding(new Coding("http://www.whocc.no/atc", order.getAtc(), order.getDisplayName())));
        if (order.getDose() != null) {
            request.addDosageInstruction().setText(order.getDose() + " " + (order.getRoute() == null ? "" : order.getRoute()));
        }
        return request;
    }

    public List<Patient> allPatients() {
        return patients.findAll().stream().map(this::toPatient).toList();
    }

    public Patient readPatient(String id) {
        return toPatient(patients.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public List<Encounter> allEncounters() {
        return encounters.findAll().stream()
                .map(entity -> toEncounter(entity, patients.findById(entity.getPatientId()).orElseThrow()))
                .toList();
    }

    public List<Encounter> encountersForPatient(String patientId) {
        return encounters.findByPatientId(patientId).stream()
                .map(entity -> toEncounter(entity, patients.findById(entity.getPatientId()).orElseThrow()))
                .toList();
    }

    public List<AllergyIntolerance> allAllergies() {
        return allergies.findAll().stream().map(this::toAllergy).toList();
    }

    public List<AllergyIntolerance> allergiesForPatient(String patientId) {
        return allergies.findByPatientId(patientId).stream().map(this::toAllergy).toList();
    }

    public List<ServiceRequest> allServiceRequests() {
        return orders.findAll().stream()
                .filter(order -> order.getKind() == OrderKind.LAB)
                .map(this::toServiceRequest)
                .toList();
    }

    public List<ServiceRequest> serviceRequestsForPatient(String patientId) {
        return orders.findByPatientIdOrderByOrderedAtDesc(patientId).stream()
                .filter(order -> order.getKind() == OrderKind.LAB)
                .map(this::toServiceRequest)
                .toList();
    }

    public List<MedicationRequest> allMedicationRequests() {
        return orders.findAll().stream()
                .filter(order -> order.getKind() == OrderKind.MEDICATION)
                .map(this::toMedication)
                .toList();
    }

    public List<MedicationRequest> medicationRequestsForPatient(String patientId) {
        return orders.findByPatientIdOrderByOrderedAtDesc(patientId).stream()
                .filter(order -> order.getKind() == OrderKind.MEDICATION)
                .map(this::toMedication)
                .toList();
    }

    public List<Observation> allObservations() {
        List<Observation> list = new ArrayList<>();
        for (ObservationEntity entity : observations.findAll()) {
            ClinicalOrderEntity order = orders.findById(entity.getOrderId()).orElse(null);
            if (order != null) {
                list.add(toObservation(entity, order));
            }
        }
        return list;
    }

    public List<Observation> observationsForPatient(String patientId) {
        List<Observation> list = new ArrayList<>();
        for (ClinicalOrderEntity order : orders.findByPatientIdOrderByOrderedAtDesc(patientId)) {
            if (order.getKind() != OrderKind.LAB) {
                continue;
            }
            for (ObservationEntity entity : observations.findByOrderIdOrderBySortOrderAsc(order.getId())) {
                list.add(toObservation(entity, order));
            }
        }
        return list;
    }

    public List<DiagnosticReport> allReports() {
        List<DiagnosticReport> list = new ArrayList<>();
        for (ClinicalOrderEntity order : orders.findAll()) {
            if (order.getKind() == OrderKind.LAB && order.getStatus() == OrderStatus.RESULTED) {
                list.add(toReport(order, observations.findByOrderIdOrderBySortOrderAsc(order.getId())));
            }
        }
        return list;
    }

    public List<DiagnosticReport> reportsForPatient(String patientId) {
        List<DiagnosticReport> list = new ArrayList<>();
        for (ClinicalOrderEntity order : orders.findByPatientIdOrderByOrderedAtDesc(patientId)) {
            if (order.getKind() == OrderKind.LAB && order.getStatus() == OrderStatus.RESULTED) {
                list.add(toReport(order, observations.findByOrderIdOrderBySortOrderAsc(order.getId())));
            }
        }
        return list;
    }

    public String patientBundleJson(String patientId) {
        PatientEntity patient = patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.addEntry().setResource(toPatient(patient));
        encounters.findFirstByPatientIdAndStatusOrderByAdmittedAtDesc(patientId, "in-progress")
                .ifPresent(encounter -> bundle.addEntry().setResource(toEncounter(encounter, patient)));
        allergies.findByPatientId(patientId).forEach(allergy -> bundle.addEntry().setResource(toAllergy(allergy)));
        for (ClinicalOrderEntity order : orders.findByPatientIdOrderByOrderedAtDesc(patientId)) {
            if (order.getKind() == OrderKind.LAB) {
                bundle.addEntry().setResource(toServiceRequest(order));
                if (order.getStatus() == OrderStatus.RESULTED) {
                    List<ObservationEntity> results = observations.findByOrderIdOrderBySortOrderAsc(order.getId());
                    results.forEach(result -> bundle.addEntry().setResource(toObservation(result, order)));
                    bundle.addEntry().setResource(toReport(order, results));
                }
            } else {
                bundle.addEntry().setResource(toMedication(order));
            }
        }
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
    }
}
