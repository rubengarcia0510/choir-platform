package com.rubengarcia.choir.formation.adapter.in.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationProximityConstraintControllerTests {

    private static final String BASE_URI =
            "/api/choirs/1/concerts/1/repertoire/1"
                    + "/formations/1/proximity-constraints";

    private final WebTestClient webTestClient;

    FormationProximityConstraintControllerTests(
            WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @Test
    void unauthenticatedListRequestIsRejected() {
        webTestClient.get()
                .uri(BASE_URI)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedCreateRequestIsRejected() {
        CsrfSession csrf = obtainCsrf();

        webTestClient.post()
                .uri(BASE_URI)
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "singerOneId", 10,
                        "singerTwoId", 20,
                        "maxDistance", 5))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedUpdateRequestIsRejected() {
        CsrfSession csrf = obtainCsrf();

        webTestClient.put()
                .uri(BASE_URI + "/1")
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "maxDistance", 5,
                        "active", true))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedDeleteRequestIsRejected() {
        CsrfSession csrf = obtainCsrf();

        webTestClient.delete()
                .uri(BASE_URI + "/1")
                .cookie("SESSION", csrf.sessionId())
                .header("X-CSRF-TOKEN", csrf.token())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private CsrfSession obtainCsrf() {
        EntityExchangeResult<Map<String, String>> result =
                webTestClient.get()
                        .uri("/api/auth/csrf")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(
                                new ParameterizedTypeReference<
                                        Map<String, String>>() {})
                        .returnResult();

        ResponseCookie cookie =
                result.getResponseCookies().getFirst("SESSION");

        assertNotNull(cookie, "El endpoint CSRF debe crear una sesión");
        assertNotNull(result.getResponseBody());

        String token = result.getResponseBody().get("token");
        assertNotNull(token, "El endpoint CSRF debe devolver un token");

        return new CsrfSession(cookie.getValue(), token);
    }

    private record CsrfSession(String sessionId, String token) {}
}
