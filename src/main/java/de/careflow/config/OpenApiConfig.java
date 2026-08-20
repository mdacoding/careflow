package de.careflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Careflow API")
                .version("1.0.0")
                .description("Stationsarbeitsplatz: Aufträge, Labor, AMTS, HL7 v2, FHIR R4. Nur synthetische Demodaten."));
    }
}
