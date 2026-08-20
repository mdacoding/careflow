package de.careflow.fhir;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.stereotype.Component;

import java.util.List;

public final class FhirProviders {

    private FhirProviders() {
    }

    static String patientId(ReferenceParam patient) {
        if (patient == null) {
            return null;
        }
        String id = patient.getIdPart();
        return (id == null || id.isBlank()) ? null : id;
    }

    @Component
    public static class PatientProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public PatientProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Patient.class;
        }

        @Read
        public Patient read(@IdParam IdType id) {
            return mapper.readPatient(id.getIdPart());
        }

        @Search
        public List<Patient> search() {
            return mapper.allPatients();
        }
    }

    @Component
    public static class EncounterProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public EncounterProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Encounter.class;
        }

        @Search
        public List<Encounter> search(@OptionalParam(name = "patient") ReferenceParam patient) {
            String patientId = FhirProviders.patientId(patient);
            return patientId == null ? mapper.allEncounters() : mapper.encountersForPatient(patientId);
        }
    }

    @Component
    public static class AllergyProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public AllergyProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return AllergyIntolerance.class;
        }

        @Search
        public List<AllergyIntolerance> search(@OptionalParam(name = "patient") ReferenceParam patient) {
            String patientId = FhirProviders.patientId(patient);
            return patientId == null ? mapper.allAllergies() : mapper.allergiesForPatient(patientId);
        }
    }

    @Component
    public static class ServiceRequestProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public ServiceRequestProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return ServiceRequest.class;
        }

        @Search
        public List<ServiceRequest> search(@OptionalParam(name = "patient") ReferenceParam patient) {
            String patientId = FhirProviders.patientId(patient);
            return patientId == null ? mapper.allServiceRequests() : mapper.serviceRequestsForPatient(patientId);
        }
    }

    @Component
    public static class ObservationProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public ObservationProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return Observation.class;
        }

        @Search
        public List<Observation> search(@OptionalParam(name = "patient") ReferenceParam patient) {
            String patientId = FhirProviders.patientId(patient);
            return patientId == null ? mapper.allObservations() : mapper.observationsForPatient(patientId);
        }
    }

    @Component
    public static class DiagnosticReportProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public DiagnosticReportProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return DiagnosticReport.class;
        }

        @Search
        public List<DiagnosticReport> search(@OptionalParam(name = "patient") ReferenceParam patient) {
            String patientId = FhirProviders.patientId(patient);
            return patientId == null ? mapper.allReports() : mapper.reportsForPatient(patientId);
        }
    }

    @Component
    public static class MedicationRequestProvider implements IResourceProvider {
        private final FhirMapper mapper;

        public MedicationRequestProvider(FhirMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Class<? extends IBaseResource> getResourceType() {
            return MedicationRequest.class;
        }

        @Search
        public List<MedicationRequest> search(@OptionalParam(name = "patient") ReferenceParam patient) {
            String patientId = FhirProviders.patientId(patient);
            return patientId == null ? mapper.allMedicationRequests() : mapper.medicationRequestsForPatient(patientId);
        }
    }
}
