package de.careflow.api;

import de.careflow.catalog.Catalog;
import de.careflow.demo.DemoDataSeeder;
import de.careflow.domain.AuditEventEntity;
import de.careflow.domain.ClinicalOrderEntity;
import de.careflow.domain.EncounterEntity;
import de.careflow.domain.Hl7MessageEntity;
import de.careflow.domain.ObservationEntity;
import de.careflow.domain.PatientEntity;
import de.careflow.fhir.FhirMapper;
import de.careflow.security.StaffDirectory;
import de.careflow.service.AuditService;
import de.careflow.service.CareflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CareflowController {

    private final CareflowService careflow;
    private final AuditService auditService;
    private final StaffDirectory staffDirectory;
    private final FhirMapper fhirMapper;

    public CareflowController(
            CareflowService careflow,
            AuditService auditService,
            StaffDirectory staffDirectory,
            FhirMapper fhirMapper) {
        this.careflow = careflow;
        this.auditService = auditService;
        this.staffDirectory = staffDirectory;
        this.fhirMapper = fhirMapper;
    }

    @GetMapping("/demo")
    public Map<String, Object> demo() {
        return Map.of(
                "clinic", "Musterklinikum Nord",
                "ward", CareflowService.WARD,
                "starPatientId", DemoDataSeeder.ELENA_ID,
                "labPreset", "BBCRP",
                "blockMed", "AMOX",
                "safeMed", "CEFU",
                "steps", List.of(
                        "Als Ärztin anmelden, Stationsboard Innere 3",
                        "Elena Krüger öffnen (Demo-Fall, Allergie Penicillin)",
                        "Laborauftrag Blutbild + CRP → HL7 ORM^O01",
                        "Labor: Auftrag annehmen, Befund freigeben → ORU^R01",
                        "CRP pathologisch; Amoxicillin — AMTS sperrt (ATC J01C)",
                        "Cefuroxim mit Kreuzallergie-Hinweis; Interop: HL7 und FHIR-Bundle"));
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return Map.of("labs", Catalog.LABS, "meds", Catalog.MEDS);
    }

    @GetMapping("/ward")
    public List<WardCard> ward() {
        return careflow.wardBoard().stream().map(overview -> {
            PatientEntity patient = overview.patient();
            return new WardCard(
                    patient.getId(),
                    patient.getMrn(),
                    patient.displayName(),
                    patient.getBirthDate().toString(),
                    patient.getSex(),
                    patient.getBed(),
                    patient.getChiefComplaint(),
                    patient.getWorkingDiagnosis(),
                    patient.getAcuity(),
                    patient.isDemoStar(),
                    overview.openLabs(),
                    overview.criticalResult(),
                    overview.allergies());
        }).toList();
    }

    @GetMapping("/patients/{id}")
    public PatientChart patient(@PathVariable String id) {
        PatientEntity patient = careflow.patient(id);
        EncounterEntity encounter = careflow.encounter(id);
        List<ClinicalOrderEntity> orders = careflow.ordersOf(id);
        return new PatientChart(
                patient.getId(),
                patient.getMrn(),
                patient.getGivenName(),
                patient.getFamilyName(),
                patient.getBirthDate().toString(),
                patient.getSex(),
                patient.getWard(),
                patient.getBed(),
                patient.getChiefComplaint(),
                patient.getWorkingDiagnosis(),
                patient.isDemoStar(),
                patient.getAcuity(),
                encounter.getId(),
                encounter.getAdmittedAt(),
                careflow.allergiesOf(id).stream()
                        .map(allergy -> new AllergyView(allergy.getSubstance(), allergy.getAtcPrefix(), allergy.getCriticality()))
                        .toList(),
                orders.stream().map(this::toOrder).toList(),
                careflow.alertsOf(id).stream()
                        .map(alert -> new AlertView(
                                alert.getId(), alert.getSeverity(), alert.getRuleId(),
                                alert.getTitle(), alert.getMessage(), alert.isOverridden()))
                        .toList());
    }

    @PostMapping("/patients/{id}/orders/lab")
    public OrderView placeLab(@PathVariable String id, @Valid @RequestBody CodeRequest request) {
        return toOrder(careflow.placeLab(staffDirectory.current(), id, request.code()));
    }

    @PostMapping("/patients/{id}/orders/medication")
    public OrderView placeMed(
            @PathVariable String id,
            @Valid @RequestBody MedRequest request) {
        return toOrder(careflow.placeMedication(
                staffDirectory.current(), id, request.code(), request.override()));
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderView cancel(@PathVariable String id) {
        return toOrder(careflow.cancel(staffDirectory.current(), id));
    }

    @GetMapping("/lab/worklist")
    public List<WorklistItem> worklist() {
        return careflow.labWorklist().stream().map(order -> {
            PatientEntity patient = careflow.patient(order.getPatientId());
            return new WorklistItem(
                    order.getId(),
                    patient.getId(),
                    patient.displayName(),
                    patient.getMrn(),
                    patient.getBed(),
                    order.getCatalogCode(),
                    order.getDisplayName(),
                    order.getStatus().name(),
                    order.getOrderedAt(),
                    patient.isDemoStar());
        }).toList();
    }

    @PostMapping("/lab/orders/{id}/accept")
    public OrderView accept(@PathVariable String id) {
        return toOrder(careflow.acceptLab(staffDirectory.current(), id));
    }

    @PostMapping("/lab/orders/{id}/release")
    public OrderView release(@PathVariable String id) {
        return toOrder(careflow.releaseLab(staffDirectory.current(), id));
    }

    @GetMapping("/interop/messages")
    public List<Hl7View> messages() {
        return careflow.hl7Log().stream().map(CareflowController::toHl7).toList();
    }

    @GetMapping("/interop/messages/{id}")
    public Hl7View message(@PathVariable String id) {
        return careflow.hl7Log().stream()
                .filter(message -> message.getId().equals(id))
                .map(CareflowController::toHl7)
                .findFirst()
                .orElseThrow();
    }

    @GetMapping(value = "/patients/{id}/fhir", produces = MediaType.APPLICATION_JSON_VALUE)
    public String fhirBundle(@PathVariable String id) {
        return fhirMapper.patientBundleJson(id);
    }

    @GetMapping("/audit")
    public List<AuditEventEntity> audit() {
        return auditService.recent();
    }

    private OrderView toOrder(ClinicalOrderEntity order) {
        return new OrderView(
                order.getId(),
                order.getPatientId(),
                order.getKind().name(),
                order.getCatalogCode(),
                order.getDisplayName(),
                order.getStatus().name(),
                order.getOrderedBy(),
                order.getOrderedAt(),
                order.getDose(),
                order.getRoute(),
                order.getAtc(),
                order.getPzn(),
                order.isBlocked(),
                order.getHl7ControlId(),
                careflow.observationsOf(order.getId()).stream()
                        .map(obs -> new ObservationView(
                                obs.getLoinc(),
                                obs.getCode(),
                                obs.getDisplayName(),
                                obs.getValueNum() == null ? null : obs.getValueNum().toPlainString(),
                                obs.getUnit(),
                                obs.getInterpretation(),
                                obs.getRefLow() == null ? null : obs.getRefLow().toPlainString(),
                                obs.getRefHigh() == null ? null : obs.getRefHigh().toPlainString()))
                        .toList(),
                careflow.hl7Of(order.getId()).stream().map(CareflowController::toHl7).toList());
    }

    private static Hl7View toHl7(Hl7MessageEntity message) {
        return new Hl7View(
                message.getId(),
                message.getOrderId(),
                message.getDirection(),
                message.getMessageType(),
                message.getControlId(),
                message.getAckCode(),
                message.getRawMessage(),
                message.getCreatedAt());
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record MedRequest(@NotBlank String code, boolean override) {
    }

    public record WardCard(
            String id,
            String mrn,
            String displayName,
            String birthDate,
            String sex,
            String bed,
            String chiefComplaint,
            String workingDiagnosis,
            String acuity,
            boolean demoStar,
            long openLabs,
            boolean criticalResult,
            List<String> allergies) {
    }

    public record AllergyView(String substance, String atcPrefix, String criticality) {
    }

    public record AlertView(
            String id, String severity, String ruleId, String title, String message, boolean overridden) {
    }

    public record ObservationView(
            String loinc,
            String code,
            String display,
            String value,
            String unit,
            String interpretation,
            String refLow,
            String refHigh) {
    }

    public record Hl7View(
            String id,
            String orderId,
            String direction,
            String messageType,
            String controlId,
            String ackCode,
            String raw,
            Instant createdAt) {
    }

    public record OrderView(
            String id,
            String patientId,
            String kind,
            String catalogCode,
            String displayName,
            String status,
            String orderedBy,
            Instant orderedAt,
            String dose,
            String route,
            String atc,
            String pzn,
            boolean blocked,
            String hl7ControlId,
            List<ObservationView> observations,
            List<Hl7View> hl7) {
    }

    public record PatientChart(
            String id,
            String mrn,
            String givenName,
            String familyName,
            String birthDate,
            String sex,
            String ward,
            String bed,
            String chiefComplaint,
            String workingDiagnosis,
            boolean demoStar,
            String acuity,
            String encounterId,
            Instant admittedAt,
            List<AllergyView> allergies,
            List<OrderView> orders,
            List<AlertView> alerts) {
    }

    public record WorklistItem(
            String orderId,
            String patientId,
            String patientName,
            String mrn,
            String bed,
            String catalogCode,
            String displayName,
            String status,
            Instant orderedAt,
            boolean demoStar) {
    }
}
