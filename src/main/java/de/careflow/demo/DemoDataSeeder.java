package de.careflow.demo;

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
import de.careflow.lab.LabResultFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    public static final String ELENA_ID = "11111111-1111-1111-1111-111111111111";
    public static final String MIRA_ID = "33333333-3333-3333-3333-333333333333";
    public static final String KARL_ID = "44444444-4444-4444-4444-444444444444";

    private final PatientRepository patients;
    private final AllergyRepository allergies;
    private final EncounterRepository encounters;
    private final ClinicalOrderRepository orders;
    private final ObservationRepository observations;
    private final LabResultFactory labResultFactory;

    public DemoDataSeeder(
            PatientRepository patients,
            AllergyRepository allergies,
            EncounterRepository encounters,
            ClinicalOrderRepository orders,
            ObservationRepository observations,
            LabResultFactory labResultFactory) {
        this.patients = patients;
        this.allergies = allergies;
        this.encounters = encounters;
        this.orders = orders;
        this.observations = observations;
        this.labResultFactory = labResultFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (patients.count() > 0) {
            return;
        }

        PatientEntity elena = patient(
                ELENA_ID, "MKN-10021", "Elena", "Krüger", LocalDate.of(1959, 3, 12), "F",
                "12", "Fieber, Husten, Dyspnoe", "Verdacht auf ambulant erworbene Pneumonie", true, "hoch");
        PatientEntity jonas = patient(
                "22222222-2222-2222-2222-222222222222", "MKN-10022", "Jonas", "Berger", LocalDate.of(1981, 11, 2), "M",
                "08", "Thoraxschmerz", "ACS nicht ausgeschlossen", false, "hoch");
        PatientEntity mira = patient(
                MIRA_ID, "MKN-10023", "Mira", "Al-Hassan", LocalDate.of(1996, 7, 19), "F",
                "15", "Flankenschmerz, Fieber", "Pyelonephritis", false, "mittel");
        PatientEntity karl = patient(
                KARL_ID, "MKN-10024", "Karl-Heinz", "Vogt", LocalDate.of(1947, 1, 8), "M",
                "03", "Belastungsdyspnoe, Ödeme", "Herzinsuffizienz NYHA III", false, "mittel");
        PatientEntity sophie = patient(
                "55555555-5555-5555-5555-555555555555", "MKN-10025", "Sophie", "Lindner", LocalDate.of(1970, 9, 30), "F",
                "11", "Allgemeinsymptome, Abklärung", "Unklare Entzündung", false, "niedrig");
        PatientEntity tobias = patient(
                "66666666-6666-6666-6666-666666666666", "MKN-10026", "Tobias", "Hartmann", LocalDate.of(1988, 4, 4), "M",
                "06", "Postoperativer Verlauf", "Zustand nach Appendektomie", false, "niedrig");

        allergy(elena.getId(), "Penicillin", "J01C", "high");
        allergy(karl.getId(), "Ibuprofen", "M01A", "low");

        encounter(elena, 18);
        encounter(jonas, 6);
        encounter(mira, 36);
        encounter(karl, 72);
        encounter(sophie, 4);
        encounter(tobias, 20);

        ClinicalOrderEntity trop = lab(jonas, "TROP", "Troponin I", OrderStatus.PLACED, 4);
        trop.setOrderedBy("Dr. med. Lena Weber");
        orders.save(trop);

        ClinicalOrderEntity krea = lab(mira, "KREA", "Kreatinin", OrderStatus.RESULTED, 30);
        krea.setOrderedBy("Dr. med. Lena Weber");
        krea.setCompletedAt(Instant.now().minus(20, ChronoUnit.HOURS));
        orders.save(krea);
        for (ObservationEntity observation : labResultFactory.create(mira, "KREA", krea.getId())) {
            observations.save(observation);
        }

        ClinicalOrderEntity kreaKarl = lab(karl, "KREA", "Kreatinin", OrderStatus.RESULTED, 24);
        kreaKarl.setOrderedBy("Dr. med. Lena Weber");
        kreaKarl.setCompletedAt(Instant.now().minus(18, ChronoUnit.HOURS));
        orders.save(kreaKarl);
        for (ObservationEntity observation : labResultFactory.create(karl, "KREA", kreaKarl.getId())) {
            observations.save(observation);
        }

        ClinicalOrderEntity bb = lab(tobias, "BB", "Kleines Blutbild", OrderStatus.IN_LAB, 2);
        bb.setOrderedBy("Dr. med. Lena Weber");
        bb.setAcceptedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        orders.save(bb);

        med(karl, "RAMI", "Ramipril", "C09AA05", "01543210", "5 mg", "PO");
        med(karl, "TORA", "Torasemid", "C03CA04", "02219887", "10 mg", "PO");
    }

    private PatientEntity patient(
            String id,
            String mrn,
            String given,
            String family,
            LocalDate birth,
            String sex,
            String bed,
            String complaint,
            String diagnosis,
            boolean star,
            String acuity) {
        PatientEntity entity = new PatientEntity();
        entity.setId(id);
        entity.setMrn(mrn);
        entity.setGivenName(given);
        entity.setFamilyName(family);
        entity.setBirthDate(birth);
        entity.setSex(sex);
        entity.setWard("Innere 3");
        entity.setBed(bed);
        entity.setDepartment("Innere Medizin");
        entity.setChiefComplaint(complaint);
        entity.setWorkingDiagnosis(diagnosis);
        entity.setDemoStar(star);
        entity.setAcuity(acuity);
        return patients.save(entity);
    }

    private void allergy(String patientId, String substance, String atc, String criticality) {
        AllergyEntity entity = new AllergyEntity();
        entity.setPatientId(patientId);
        entity.setSubstance(substance);
        entity.setAtcPrefix(atc);
        entity.setCriticality(criticality);
        allergies.save(entity);
    }

    private void encounter(PatientEntity patient, int hoursAgo) {
        EncounterEntity entity = new EncounterEntity();
        entity.setPatientId(patient.getId());
        entity.setStatus("in-progress");
        entity.setAdmittedAt(Instant.now().minus(hoursAgo, ChronoUnit.HOURS));
        entity.setDepartment(patient.getDepartment());
        encounters.save(entity);
    }

    private ClinicalOrderEntity lab(PatientEntity patient, String code, String display, OrderStatus status, int hoursAgo) {
        EncounterEntity encounter = encounters
                .findFirstByPatientIdAndStatusOrderByAdmittedAtDesc(patient.getId(), "in-progress")
                .orElseThrow();
        ClinicalOrderEntity order = new ClinicalOrderEntity();
        order.setPatientId(patient.getId());
        order.setEncounterId(encounter.getId());
        order.setKind(OrderKind.LAB);
        order.setCatalogCode(code);
        order.setDisplayName(display);
        order.setStatus(status);
        order.setOrderedAt(Instant.now().minus(hoursAgo, ChronoUnit.HOURS));
        order.setPlacerNumber("PLC-" + code + patient.getBed());
        return order;
    }

    private void med(PatientEntity patient, String code, String display, String atc, String pzn, String dose, String route) {
        EncounterEntity encounter = encounters
                .findFirstByPatientIdAndStatusOrderByAdmittedAtDesc(patient.getId(), "in-progress")
                .orElseThrow();
        ClinicalOrderEntity order = new ClinicalOrderEntity();
        order.setPatientId(patient.getId());
        order.setEncounterId(encounter.getId());
        order.setKind(OrderKind.MEDICATION);
        order.setCatalogCode(code);
        order.setDisplayName(display);
        order.setStatus(OrderStatus.ACTIVE);
        order.setAtc(atc);
        order.setPzn(pzn);
        order.setDose(dose);
        order.setRoute(route);
        order.setOrderedBy("Dr. med. Lena Weber");
        order.setOrderedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        orders.save(order);
    }
}
