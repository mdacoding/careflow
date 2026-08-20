package de.careflow.service;

import de.careflow.catalog.Catalog;
import de.careflow.cds.CdsBlockException;
import de.careflow.cds.CdsEngine;
import de.careflow.domain.AllergyEntity;
import de.careflow.domain.AllergyRepository;
import de.careflow.domain.CdsAlertEntity;
import de.careflow.domain.CdsAlertRepository;
import de.careflow.domain.ClinicalOrderEntity;
import de.careflow.domain.ClinicalOrderRepository;
import de.careflow.domain.EncounterEntity;
import de.careflow.domain.EncounterRepository;
import de.careflow.domain.Hl7MessageEntity;
import de.careflow.domain.Hl7MessageRepository;
import de.careflow.domain.ObservationEntity;
import de.careflow.domain.ObservationRepository;
import de.careflow.domain.OrderKind;
import de.careflow.domain.OrderStatus;
import de.careflow.domain.PatientEntity;
import de.careflow.domain.PatientRepository;
import de.careflow.hl7.Hl7Gateway;
import de.careflow.lab.LabResultFactory;
import de.careflow.realtime.CareflowSocketHandler;
import de.careflow.security.Staff;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CareflowService {

    public static final String WARD = "Innere 3";

    private final PatientRepository patients;
    private final AllergyRepository allergies;
    private final EncounterRepository encounters;
    private final ClinicalOrderRepository orders;
    private final ObservationRepository observations;
    private final CdsAlertRepository alerts;
    private final Hl7MessageRepository hl7Messages;
    private final AuditService auditService;
    private final Hl7Gateway hl7Gateway;
    private final CdsEngine cdsEngine;
    private final LabResultFactory labResultFactory;
    private final CareflowSocketHandler socketHandler;

    public CareflowService(
            PatientRepository patients,
            AllergyRepository allergies,
            EncounterRepository encounters,
            ClinicalOrderRepository orders,
            ObservationRepository observations,
            CdsAlertRepository alerts,
            Hl7MessageRepository hl7Messages,
            AuditService auditService,
            Hl7Gateway hl7Gateway,
            CdsEngine cdsEngine,
            LabResultFactory labResultFactory,
            CareflowSocketHandler socketHandler) {
        this.patients = patients;
        this.allergies = allergies;
        this.encounters = encounters;
        this.orders = orders;
        this.observations = observations;
        this.alerts = alerts;
        this.hl7Messages = hl7Messages;
        this.auditService = auditService;
        this.hl7Gateway = hl7Gateway;
        this.cdsEngine = cdsEngine;
        this.labResultFactory = labResultFactory;
        this.socketHandler = socketHandler;
    }

    public List<PatientEntity> ward() {
        return patients.findAllByWardOrderByBedAsc(WARD);
    }

    public List<WardOverview> wardBoard() {
        List<PatientEntity> patients = ward();
        List<String> ids = patients.stream().map(PatientEntity::getId).toList();
        Map<String, List<ClinicalOrderEntity>> ordersByPatient = ids.isEmpty()
                ? Map.of()
                : orders.findByPatientIdIn(ids).stream().collect(Collectors.groupingBy(ClinicalOrderEntity::getPatientId));
        Map<String, List<AllergyEntity>> allergiesByPatient = ids.isEmpty()
                ? Map.of()
                : allergies.findByPatientIdIn(ids).stream().collect(Collectors.groupingBy(AllergyEntity::getPatientId));
        List<String> labOrderIds = ordersByPatient.values().stream()
                .flatMap(List::stream)
                .filter(order -> order.getKind() == OrderKind.LAB)
                .map(ClinicalOrderEntity::getId)
                .toList();
        Map<String, List<ObservationEntity>> observationsByOrder = labOrderIds.isEmpty()
                ? Map.of()
                : observations.findByOrderIdIn(labOrderIds).stream().collect(Collectors.groupingBy(ObservationEntity::getOrderId));
        return patients.stream().map(patient -> {
            List<ClinicalOrderEntity> patientOrders = ordersByPatient.getOrDefault(patient.getId(), List.of());
            long openLabs = patientOrders.stream()
                    .filter(order -> order.getKind() == OrderKind.LAB
                            && (order.getStatus() == OrderStatus.PLACED || order.getStatus() == OrderStatus.IN_LAB))
                    .count();
            boolean criticalResult = patientOrders.stream()
                    .filter(order -> order.getKind() == OrderKind.LAB)
                    .flatMap(order -> observationsByOrder.getOrDefault(order.getId(), List.of()).stream())
                    .anyMatch(ObservationEntity::abnormal);
            List<String> allergyNames = allergiesByPatient.getOrDefault(patient.getId(), List.of()).stream()
                    .map(AllergyEntity::getSubstance)
                    .toList();
            return new WardOverview(patient, allergyNames, openLabs, criticalResult);
        }).toList();
    }

    public record WardOverview(PatientEntity patient, List<String> allergies, long openLabs, boolean criticalResult) {
    }

    public PatientEntity patient(String id) {
        return patients.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public EncounterEntity encounter(String patientId) {
        return encounters.findFirstByPatientIdAndStatusOrderByAdmittedAtDesc(patientId, "in-progress")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kein aktiver Fall"));
    }

    public List<AllergyEntity> allergiesOf(String patientId) {
        return allergies.findByPatientId(patientId);
    }

    public List<ClinicalOrderEntity> ordersOf(String patientId) {
        return orders.findByPatientIdOrderByOrderedAtDesc(patientId);
    }

    public List<ObservationEntity> observationsOf(String orderId) {
        return observations.findByOrderIdOrderBySortOrderAsc(orderId);
    }

    public List<ClinicalOrderEntity> labWorklist() {
        return orders.findByKindAndStatusInOrderByOrderedAtAsc(
                OrderKind.LAB, EnumSet.of(OrderStatus.PLACED, OrderStatus.IN_LAB));
    }

    public List<Hl7MessageEntity> hl7Log() {
        return hl7Messages.findAllByOrderByCreatedAtDesc();
    }

    public List<Hl7MessageEntity> hl7Of(String orderId) {
        return hl7Messages.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    public List<CdsAlertEntity> alertsOf(String patientId) {
        return alerts.findByPatientIdOrderByIdDesc(patientId);
    }

    @Transactional
    public ClinicalOrderEntity placeLab(Staff staff, String patientId, String catalogCode) {
        requireRole(staff, "PHYSICIAN");
        Catalog.LabItem item = Catalog.lab(catalogCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannter Laborkatalog"));
        boolean openDuplicate = orders.findByPatientIdAndKindAndStatusIn(
                        patientId, OrderKind.LAB, EnumSet.of(OrderStatus.PLACED, OrderStatus.IN_LAB))
                .stream()
                .anyMatch(existing -> Catalog.labConflicts(catalogCode, existing.getCatalogCode()));
        if (openDuplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Offener Laborauftrag überlappt mit diesem Panel");
        }
        PatientEntity patient = patient(patientId);
        EncounterEntity encounter = encounter(patientId);
        ClinicalOrderEntity order = new ClinicalOrderEntity();
        order.setPatientId(patientId);
        order.setEncounterId(encounter.getId());
        order.setKind(OrderKind.LAB);
        order.setCatalogCode(item.code());
        order.setDisplayName(item.display());
        order.setStatus(OrderStatus.PLACED);
        order.setOrderedBy(staff.displayName());
        order.setOrderedAt(Instant.now());
        order.setPlacerNumber("PLC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        orders.save(order);

        Hl7Gateway.ParsedMessage orm = hl7Gateway.orm(patient, order);
        order.setHl7ControlId(orm.controlId());
        persistHl7(order.getId(), "OUTBOUND", orm);
        Hl7Gateway.ParsedMessage ack = hl7Gateway.ack(orm.controlId(), "O01");
        persistHl7(order.getId(), "INBOUND", ack);

        auditService.record(staff, "Laborauftrag übermittelt", "ClinicalOrder", order.getId(), item.display());
        socketHandler.publish("ORDER_PLACED", patientId, order.getId(), "Laborauftrag " + item.display());
        return order;
    }

    @Transactional
    public ClinicalOrderEntity acceptLab(Staff staff, String orderId) {
        requireRole(staff, "LAB", "PHYSICIAN");
        ClinicalOrderEntity order = order(orderId);
        de.careflow.domain.OrderStateMachine.require(order.getKind(), order.getStatus(), OrderStatus.IN_LAB);
        order.setStatus(OrderStatus.IN_LAB);
        order.setAcceptedAt(Instant.now());
        auditService.record(staff, "Laborauftrag angenommen", "ClinicalOrder", order.getId(), order.getDisplayName());
        socketHandler.publish("ORDER_ACCEPTED", order.getPatientId(), order.getId(), "Labor hat angenommen");
        return order;
    }

    @Transactional
    public ClinicalOrderEntity releaseLab(Staff staff, String orderId) {
        requireRole(staff, "LAB", "PHYSICIAN");
        ClinicalOrderEntity order = order(orderId);
        if (order.getStatus() == OrderStatus.PLACED) {
            order.setStatus(OrderStatus.IN_LAB);
            order.setAcceptedAt(Instant.now());
        }
        de.careflow.domain.OrderStateMachine.require(order.getKind(), order.getStatus(), OrderStatus.RESULTED);
        PatientEntity patient = patient(order.getPatientId());
        List<ObservationEntity> results = labResultFactory.create(patient, order.getCatalogCode(), order.getId());
        observations.saveAll(results);
        order.setStatus(OrderStatus.RESULTED);
        order.setCompletedAt(Instant.now());
        Hl7Gateway.ParsedMessage oru = hl7Gateway.oru(patient, order, results);
        persistHl7(order.getId(), "INBOUND", oru);
        persistHl7(order.getId(), "OUTBOUND", hl7Gateway.ack(oru.controlId(), "R01"));
        auditService.record(staff, "Befund freigegeben", "ClinicalOrder", order.getId(), order.getDisplayName());
        socketHandler.publish("RESULT_READY", order.getPatientId(), order.getId(), "Befund " + order.getDisplayName());
        return order;
    }

    @Transactional
    public ClinicalOrderEntity placeMedication(Staff staff, String patientId, String catalogCode, boolean override) {
        requireRole(staff, "PHYSICIAN");
        Catalog.MedItem item = Catalog.med(catalogCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekanntes Arzneimittel"));
        PatientEntity patient = patient(patientId);
        EncounterEntity encounter = encounter(patientId);
        List<CdsEngine.Finding> findings = cdsEngine.evaluate(new CdsEngine.Request(
                item.code(),
                item.atc(),
                item.display(),
                allergiesOf(patientId).stream()
                        .map(allergy -> new CdsEngine.Allergy(allergy.getSubstance(), allergy.getAtcPrefix()))
                        .toList(),
                orders.findByPatientIdAndKindAndStatusIn(
                                patientId, OrderKind.MEDICATION, EnumSet.of(OrderStatus.ACTIVE))
                        .stream()
                        .map(active -> new CdsEngine.ActiveMed(active.getCatalogCode(), active.getAtc(), active.getDisplayName()))
                        .toList(),
                patient.getWorkingDiagnosis(),
                latestCreatinine(patientId),
                Period.between(patient.getBirthDate(), LocalDate.now()).getYears(),
                patient.getSex()));

        boolean blocking = findings.stream().anyMatch(CdsEngine.Finding::blocking);
        if (blocking && !override) {
            throw new CdsBlockException(findings);
        }

        ClinicalOrderEntity order = new ClinicalOrderEntity();
        order.setPatientId(patientId);
        order.setEncounterId(encounter.getId());
        order.setKind(OrderKind.MEDICATION);
        order.setCatalogCode(item.code());
        order.setDisplayName(item.display());
        order.setDose(item.dose());
        order.setRoute(item.route());
        order.setAtc(item.atc());
        order.setPzn(item.pzn());
        order.setOrderedBy(staff.displayName());
        order.setOrderedAt(Instant.now());
        if (blocking) {
            order.setStatus(OrderStatus.BLOCKED);
            order.setBlocked(true);
            order.setNotes("AMTS-Override durch " + staff.displayName());
        } else {
            order.setStatus(OrderStatus.ACTIVE);
        }
        orders.save(order);
        persistFindings(patientId, order.getId(), findings, override && blocking);
        auditService.record(staff, blocking ? "Verordnung durch AMTS gesperrt" : "Verordnung aktiv", "ClinicalOrder", order.getId(), item.display());
        socketHandler.publish(
                blocking ? "MEDICATION_BLOCKED" : "MEDICATION_ORDERED",
                patientId,
                order.getId(),
                item.display());
        return order;
    }

    @Transactional
    public ClinicalOrderEntity cancel(Staff staff, String orderId) {
        requireRole(staff, "PHYSICIAN");
        ClinicalOrderEntity order = order(orderId);
        de.careflow.domain.OrderStateMachine.require(order.getKind(), order.getStatus(), OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        auditService.record(staff, "Auftrag storniert", "ClinicalOrder", order.getId(), order.getDisplayName());
        socketHandler.publish("ORDER_CANCELLED", order.getPatientId(), order.getId(), order.getDisplayName());
        return order;
    }

    public ClinicalOrderEntity order(String id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Double latestCreatinine(String patientId) {
        return ordersOf(patientId).stream()
                .filter(order -> "KREA".equals(order.getCatalogCode()) && order.getStatus() == OrderStatus.RESULTED)
                .findFirst()
                .flatMap(order -> observationsOf(order.getId()).stream().findFirst())
                .map(ObservationEntity::getValueNum)
                .map(BigDecimal::doubleValue)
                .orElse(null);
    }

    private void persistFindings(String patientId, String orderId, List<CdsEngine.Finding> findings, boolean overridden) {
        for (CdsEngine.Finding finding : findings) {
            CdsAlertEntity entity = new CdsAlertEntity();
            entity.setPatientId(patientId);
            entity.setOrderId(orderId);
            entity.setSeverity(finding.severity());
            entity.setRuleId(finding.ruleId());
            entity.setTitle(finding.title());
            entity.setMessage(finding.message());
            entity.setOverridden(overridden);
            alerts.save(entity);
        }
    }

    private void persistHl7(String orderId, String direction, Hl7Gateway.ParsedMessage message) {
        Hl7MessageEntity entity = new Hl7MessageEntity();
        entity.setOrderId(orderId);
        entity.setDirection(direction);
        entity.setMessageType(message.messageType());
        entity.setControlId(message.controlId());
        entity.setAckCode(message.ackCode());
        entity.setRawMessage(message.raw());
        hl7Messages.save(entity);
    }

    private static void requireRole(Staff staff, String... roles) {
        for (String role : roles) {
            if (role.equals(staff.role())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rolle " + staff.role() + " darf diese Aktion nicht ausführen");
    }
}
