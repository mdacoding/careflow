package de.careflow.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import de.careflow.fhir.FhirProviders;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class FhirServletConfig {

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServlet(
            FhirContext fhirContext,
            FhirProviders.PatientProvider patientProvider,
            FhirProviders.EncounterProvider encounterProvider,
            FhirProviders.AllergyProvider allergyProvider,
            FhirProviders.ServiceRequestProvider serviceRequestProvider,
            FhirProviders.ObservationProvider observationProvider,
            FhirProviders.DiagnosticReportProvider diagnosticReportProvider,
            FhirProviders.MedicationRequestProvider medicationRequestProvider) {
        RestfulServer server = new RestfulServer(fhirContext);
        server.setDefaultPrettyPrint(true);
        server.setDefaultResponseEncoding(EncodingEnum.JSON);
        server.setServerName("Careflow FHIR");
        server.setServerVersion("1.0.0");
        server.setImplementationDescription(
                "FHIR-R4-Projektion des Stationsarbeitsplatzes (Patient, Encounter, ServiceRequest, Observation, DiagnosticReport, MedicationRequest). Synthetische Demodaten, keine gematik-Bestätigung.");
        server.registerProviders(
                patientProvider,
                encounterProvider,
                allergyProvider,
                serviceRequestProvider,
                observationProvider,
                diagnosticReportProvider,
                medicationRequestProvider);
        CorsConfiguration cors = new CorsConfiguration();
        cors.addAllowedOriginPattern("*");
        cors.addAllowedHeader("*");
        cors.addAllowedMethod("*");
        server.registerInterceptor(new CorsInterceptor(cors));
        server.registerInterceptor(new ResponseHighlighterInterceptor());

        ServletRegistrationBean<RestfulServer> registration = new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("fhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
