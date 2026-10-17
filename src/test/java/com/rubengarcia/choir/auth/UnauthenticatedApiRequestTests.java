package com.rubengarcia.choir.auth;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class UnauthenticatedApiRequestTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedApiPathsRejectUnauthenticatedRequests() {
        List<String> protectedPaths = List.of(
                "/api/choirs",
                "/api/singers",
                "/api/formations",
                "/api/concerts",
                "/api/absences",
                "/api/repertoire"
        );

        protectedPaths.forEach(path ->
                webTestClient.get()
                        .uri(path)
                        .exchange()
                        .expectStatus().isUnauthorized()
        );
    }
}
