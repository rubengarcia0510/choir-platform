package com.rubengarcia.choir.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DirectorRegistrationEndpointTests {

    private final WebTestClient webTestClient;
    private final ObjectMapper objectMapper;

    DirectorRegistrationEndpointTests(
            WebTestClient webTestClient,
            ObjectMapper objectMapper) {
        this.webTestClient = webTestClient;
        this.objectMapper = objectMapper;
    }

    @Test
    void registrationIsUnavailableByDefault() throws Exception {
        EntityExchangeResult<Map<String, String>> csrfResult =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<Map<String, String>>() {})
                        .returnResult();

        ResponseCookie sessionCookie =
                csrfResult.getResponseCookies().getFirst("SESSION");

        assertNotNull(sessionCookie, "Debe crearse una sesión para CSRF");

        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken);

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "displayName", "Registration Test",
                "email", "registration-test@example.test",
                "password", "Choir-Test-Password-2026!"
        ));

        webTestClient.post()
                .uri("/api/auth/register")
                .cookie("SESSION", sessionCookie.getValue())
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("registration_unavailable"));
                    assertTrue(!body.contains("token="),
                            "La respuesta no debe exponer tokens de verificación");
                    assertTrue(!body.contains("Choir-Test-Password-2026!"),
                            "La respuesta no debe exponer la contraseña");
                });
    }
}
