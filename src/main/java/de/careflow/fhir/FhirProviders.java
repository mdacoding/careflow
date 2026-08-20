package de.careflow.fhir;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
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
        public List<Encounter> search() {
            return mapper.allEncounters();
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
        public List<AllergyIntolerance> search() {
            return mapper.allAllergies();
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
        public List<ServiceRequest> search() {
            return mapper.allServiceRequests();
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
        public List<Observation> search() {
            return mapper.allObservations();
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
        public List<DiagnosticReport> search() {
            return mapper.allReports();
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
        public List<MedicationRequest> search() {
            return mapper.allMedicationRequests();
        }
    }
}
