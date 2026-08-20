package de.careflow;

import com.fasterxml.jackson.databind.json.JsonMapper;
import de.careflow.demo.DemoDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CareflowApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestRestTemplate rest;

    @LocalServerPort
    int port;

    private MockHttpSession physician;
    private MockHttpSession lab;

    @BeforeEach
    void login() throws Exception {
        physician = session("weber");
        lab = session("hoffmann");
    }

    @Test
    void labRoundtripAndAmtsBlock() throws Exception {
        mvc.perform(get("/api/ward").session(physician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.demoStar==true)].mrn").value("MKN-10021"));

        MvcResult created = mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BBCRP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.hl7[0].messageType").value("ORM^O01"))
                .andReturn();

        String orderId = jsonId(created);

        mvc.perform(post("/api/lab/orders/" + orderId + "/release").session(lab))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULTED"))
                .andExpect(jsonPath("$.observations[?(@.code=='CRP')].interpretation").value("HH"));

        mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/medication")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AMOX\",\"override\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CDS_BLOCK"));

        mvc.perform(get("/api/patients/" + DemoDataSeeder.ELENA_ID + "/fhir").session(physician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.entry[0].resource.resourceType").value("Patient"));

        ResponseEntity<String> fhir = rest.getForEntity("/fhir/Patient?_format=json", String.class);
        assertThat(fhir.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(fhir.getBody()).contains("Bundle").contains("Patient");
    }

    @Test
    void websocketIsNotAnonymous() throws Exception {
        mvc.perform(get("/api/ws"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nurseCannotOrderMedication() throws Exception {
        MockHttpSession nurse = session("schmidt");
        mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/medication")
                        .session(nurse)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PARA\",\"override\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void physicianCanCancelPlacedLabButNurseCannot() throws Exception {
        MockHttpSession nurse = session("schmidt");
        MvcResult created = mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BBCRP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn();
        String bbcRpId = jsonId(created);
        String bbId = null;
        try {
            mvc.perform(post("/api/orders/" + bbcRpId + "/cancel").session(nurse))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/orders/" + bbcRpId + "/cancel").session(physician))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.hl7[*].messageType").value(org.hamcrest.Matchers.hasItem("ORM^O01")))
                    .andExpect(jsonPath("$.hl7[*].raw").value(
                            org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("ORC|CA"))));
            bbId = jsonId(mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                            .session(physician)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"BB\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.catalogCode").value("BB"))
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andReturn());
        } finally {
            if (bbId != null) {
                mvc.perform(post("/api/orders/" + bbId + "/cancel").session(physician))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void labAcceptPersistsInboundStatusOrmSc() throws Exception {
        MvcResult created = mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BGA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn();
        String orderId = jsonId(created);
        try {
            MvcResult accepted = mvc.perform(post("/api/lab/orders/" + orderId + "/accept").session(lab))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_LAB"))
                    .andExpect(jsonPath("$.hl7[*].messageType").value(org.hamcrest.Matchers.hasItem("ORM^O01")))
                    .andExpect(jsonPath("$.hl7[*].raw").value(
                            org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("ORC|SC"))))
                    .andReturn();
            var hl7 = JsonMapper.builder().build()
                    .readTree(accepted.getResponse().getContentAsString())
                    .get("hl7");
            boolean inboundScFromLab = false;
            boolean outboundAckFromCareflow = false;
            for (var message : hl7) {
                String direction = message.path("direction").asText();
                String type = message.path("messageType").asText();
                String raw = message.path("raw").asText();
                if ("INBOUND".equals(direction) && raw.contains("ORC|SC")) {
                    inboundScFromLab = true;
                    assertThat(mshApps(raw)).containsExactly("LABSYS", "CAREFLOW");
                }
                if ("OUTBOUND".equals(direction) && type.startsWith("ACK")) {
                    outboundAckFromCareflow = true;
                    assertThat(mshApps(raw)).containsExactly("CAREFLOW", "LABSYS");
                    assertThat(message.path("ackCode").asText()).isEqualTo("AA");
                }
            }
            assertThat(inboundScFromLab).isTrue();
            assertThat(outboundAckFromCareflow).isTrue();
        } finally {
            mvc.perform(post("/api/orders/" + orderId + "/cancel").session(physician))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void overlappingLabPanelReturnsConflictWhileTropRemainsAllowed() throws Exception {
        MvcResult created = mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BBCRP\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String bbcRpId = jsonId(created);
        String tropId = null;
        try {
            mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                            .session(physician)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"BB\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                            .session(physician)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"CRP\"}"))
                    .andExpect(status().isConflict());
            tropId = jsonId(mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                            .session(physician)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"TROP\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.catalogCode").value("TROP"))
                    .andReturn());
        } finally {
            if (tropId != null) {
                mvc.perform(post("/api/orders/" + tropId + "/cancel").session(physician))
                        .andExpect(status().isOk());
            }
            mvc.perform(post("/api/orders/" + bbcRpId + "/cancel").session(physician))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void loginSetsHttpOnlySameSiteLaxCookieWithoutSecure() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"weber\",\"password\":\"demo\"}"))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        List<String> setCookie = response.headers().allValues("set-cookie");
        assertThat(setCookie).isNotEmpty();
        String cookie = String.join("; ", setCookie);
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).containsIgnoringCase("SameSite=Lax");
        boolean secureFlag = java.util.Arrays.stream(cookie.split(";"))
                .map(String::trim)
                .anyMatch(part -> part.equalsIgnoreCase("Secure"));
        assertThat(secureFlag).as("Secure bleibt aus für lokale HTTP-Demo und Vite-Proxy").isFalse();
    }

    @Test
    void patientChartExposesCreatinineAndEgfrWhenKreaResulted() throws Exception {
        mvc.perform(get("/api/patients/" + DemoDataSeeder.MIRA_ID).session(physician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatinineMgDl").isNumber())
                .andExpect(jsonPath("$.egfrMlMin").isNumber());
        mvc.perform(get("/api/patients/" + DemoDataSeeder.KARL_ID).session(physician))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatinineMgDl").isNumber())
                .andExpect(jsonPath("$.egfrMlMin").isNumber())
                .andExpect(jsonPath("$.egfrMlMin").value(org.hamcrest.Matchers.lessThan(60)));
    }

    @Test
    void auditContainsLabOrderDtoAfterPhysicianPlacesLab() throws Exception {
        MvcResult created = mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/lab")
                        .session(physician)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BGA\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = jsonId(created);
        try {
            mvc.perform(get("/api/audit").session(physician))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.action=='Laborauftrag übermittelt')]").isNotEmpty())
                    .andExpect(jsonPath("$[0].id").exists())
                    .andExpect(jsonPath("$[0].actor").exists())
                    .andExpect(jsonPath("$[0].actorRole").exists())
                    .andExpect(jsonPath("$[0].createdAt").exists());
        } finally {
            mvc.perform(post("/api/orders/" + orderId + "/cancel").session(physician))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void fhirObservationSearchFiltersByPatient() {
        ResponseEntity<String> filtered = rest.getForEntity(
                "/fhir/Observation?patient=" + DemoDataSeeder.MIRA_ID + "&_format=json", String.class);
        assertThat(filtered.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(filtered.getBody()).contains("Observation");
        assertThat(filtered.getBody()).contains(DemoDataSeeder.MIRA_ID);
        assertThat(filtered.getBody()).doesNotContain("MKN-10021");

        ResponseEntity<String> patients = rest.getForEntity("/fhir/Patient?_format=json", String.class);
        assertThat(patients.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(patients.getBody()).contains("Bundle").contains("Patient");
    }

    @Test
    void fhirMetadataReturnsCapabilityStatementUnauthenticated() throws Exception {
        ResponseEntity<String> metadata = rest.getForEntity("/fhir/metadata?_format=json", String.class);
        assertThat(metadata.getStatusCode().is2xxSuccessful()).isTrue();
        String resourceType = JsonMapper.builder().build()
                .readTree(metadata.getBody())
                .path("resourceType")
                .asText();
        assertThat(resourceType).isEqualTo("CapabilityStatement");
    }

    @Test
    void healthResponseIncludesNosniffAndReferrerPolicy() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(health.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(health.getHeaders().getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(health.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }

    private MockHttpSession session(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"demo\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String jsonId(MvcResult result) throws Exception {
        return JsonMapper.builder().build()
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asText();
    }

    private static String[] mshApps(String raw) {
        String msh = raw.lines().findFirst().orElse("");
        String[] fields = msh.split("\\|", -1);
        return new String[] {fields[2], fields[4]};
    }
}
