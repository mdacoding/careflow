package de.careflow;

import de.careflow.demo.DemoDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

        String orderId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText();

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
    void nurseCannotOrderMedication() throws Exception {
        MockHttpSession nurse = session("schmidt");
        mvc.perform(post("/api/patients/" + DemoDataSeeder.ELENA_ID + "/orders/medication")
                        .session(nurse)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"PARA\",\"override\":false}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession session(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"demo\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
