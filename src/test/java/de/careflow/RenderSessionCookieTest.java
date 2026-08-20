package de.careflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.servlet.session.cookie.secure=true")
class RenderSessionCookieTest {

    @LocalServerPort
    int port;

    @Test
    void loginSetsSecureCookieWhenRenderEnvIsSet() throws Exception {
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
        assertThat(secureFlag).as("Docker/Render setzt cookie.secure=true").isTrue();
    }
}
