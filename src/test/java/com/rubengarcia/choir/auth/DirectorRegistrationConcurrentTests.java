package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "choir.auth.registration.enabled=true",
        "choir.auth.verification-url-base=https://example.test/verify"
})
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DirectorRegistrationConcurrentTests {

    private static final int REQUEST_COUNT = 16;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebTestClient webTestClient;
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    @MockitoSpyBean
    private DirectorRegistrationLookup registrationLookup;

    @MockitoBean
    private VerificationEmailSender emailSender;

    DirectorRegistrationConcurrentTests(
            WebTestClient webTestClient,
            DatabaseClient databaseClient,
            ObjectMapper objectMapper) {
        this.webTestClient = webTestClient;
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Test
    void concurrentRegistrationsWithSameEmailCreateOneAccountAndReturnGenericResponse()
            throws Exception {

        String email = "concurrent-" + UUID.randomUUID() + "@example.test";

        Sinks.One<Void> bothLookupsStarted = Sinks.one();
        AtomicInteger startedLookups = new AtomicInteger();

        /*
         * El spy está sobre una clase concreta. La llamada real delega
         * en DirectorRepository, y las dos primeras consultas quedan
         * retenidas hasta que ambas hayan comenzado.
         */
        doAnswer(invocation -> {
            String queriedEmail = invocation.getArgument(0);

            @SuppressWarnings("unchecked")
            Mono<Director> actualQuery =
                    (Mono<Director>) invocation.callRealMethod();

            if (!email.equalsIgnoreCase(queriedEmail)) {
                return actualQuery;
            }

            return Mono.defer(() -> {
                int started = startedLookups.incrementAndGet();

                if (started <= 2) {
                    if (started == 2) {
                        bothLookupsStarted.tryEmitEmpty();
                    }

                    return bothLookupsStarted.asMono()
                            .timeout(TIMEOUT)
                            .then(actualQuery);
                }

                return actualQuery;
            });
        }).when(registrationLookup).findByEmailIgnoreCase(anyString());

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

        assertNotNull(csrfToken, "Debe obtenerse el token CSRF");

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "displayName", "Concurrent Registration",
                "email", email,
                "password", "Choir-Test-Password-2026!"
        ));

        ExecutorService executor =
                Executors.newFixedThreadPool(REQUEST_COUNT);

        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<CompletableFuture<ResponseSnapshot>> requests =
                    new ArrayList<>();

            for (int i = 0; i < REQUEST_COUNT; i++) {
                requests.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();

                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "No se liberó la barrera de inicio");
                        }

                        EntityExchangeResult<String> result =
                                webTestClient.post()
                                        .uri("/api/auth/register")
                                        .cookie(
                                                "SESSION",
                                                sessionCookie.getValue())
                                        .header("X-CSRF-TOKEN", csrfToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue(requestBody)
                                        .exchange()
                                        .expectBody(String.class)
                                        .returnResult();

                        return new ResponseSnapshot(
                                result.getStatus().value(),
                                result.getResponseBody());

                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }, executor));
            }

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Todas las solicitudes deben estar listas antes de iniciarlas");

            start.countDown();

            CompletableFuture.allOf(
                    requests.toArray(new CompletableFuture[0]))
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            List<ResponseSnapshot> responses =
                    requests.stream()
                            .map(CompletableFuture::join)
                            .toList();

            assertEquals(REQUEST_COUNT, responses.size());

            assertTrue(
                    startedLookups.get() >= 2,
                    "Al menos dos consultas deben comenzar antes de continuar; "
                            + "iniciadas: " + startedLookups.get());

            assertTrue(
                    responses.stream()
                            .allMatch(response -> response.status() == 202),
                    () -> "Todas las respuestas deben ser HTTP 202: " + responses);

            String genericResponse = responses.get(0).body();

            assertNotNull(genericResponse);

            assertTrue(
                    responses.stream()
                            .allMatch(response ->
                                    genericResponse.equals(response.body())),
                    "Todas las respuestas deben tener el mismo cuerpo genérico");

            assertTrue(
                    !genericResponse.contains(email),
                    "La respuesta no debe revelar el email registrado");

            assertTrue(
                    !genericResponse.contains("token="),
                    "La respuesta no debe revelar el token de verificación");

            Long accountCount = databaseClient.sql(
                            "SELECT COUNT(*) AS total FROM directors "
                                    + "WHERE email = :email")
                    .bind("email", email)
                    .map((row, metadata) -> row.get("total", Long.class))
                    .one()
                    .block(TIMEOUT);

            assertEquals(
                    1L,
                    accountCount,
                    "Debe existir exactamente una cuenta para el email concurrente");

            Long tokenCount = databaseClient.sql("""
                            SELECT COUNT(*) AS total
                            FROM director_email_verification_tokens t
                            JOIN directors d ON d.id = t.director_id
                            WHERE d.email = :email
                            """)
                    .bind("email", email)
                    .map((row, metadata) -> row.get("total", Long.class))
                    .one()
                    .block(TIMEOUT);

            assertEquals(
                    1L,
                    tokenCount,
                    "Debe quedar exactamente un token de verificación");

            verify(emailSender, times(1))
                    .sendVerificationEmail(
                            anyString(),
                            anyString(),
                            anyString(),
                            any());

        } finally {
            start.countDown();
            bothLookupsStarted.tryEmitEmpty();
            executor.shutdownNow();
        }
    }

    private record ResponseSnapshot(int status, String body) {
    }
}
