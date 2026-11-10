package com.rubengarcia.choir.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ConcertOptimizationPreferencesControllerTests {

    private static final String PASSWORD = "Optimization-Test-Password-2026!";

    private final WebTestClient web;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final List<Long> directorIds = new ArrayList<>();

    ConcertOptimizationPreferencesControllerTests(
            WebTestClient web,
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder) {
        this.web = web;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanup() {
        for (Long directorId : directorIds) {
            jdbc.update("""
                    DELETE FROM concerts
                    WHERE choir_id IN (
                        SELECT id FROM choirs WHERE director_id = ?)
                    """, directorId);
            jdbc.update(
                    "DELETE FROM choirs WHERE director_id = ?", directorId);
            jdbc.update(
                    "DELETE FROM directors WHERE id = ?", directorId);
        }
        directorIds.clear();
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        web.get()
                .uri(path(1L, 1L))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getReturnsDefaultsWithoutPersistingPreferences() {
        Fixture fixture = fixture("defaults");
        Session session = login(fixture.email());

        web.get()
                .uri(path(fixture.choirId(), fixture.concertId()))
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.distance").isEqualTo(25)
                .jsonPath("$.crossings").isEqualTo(25)
                .jsonPath("$.compactness").isEqualTo(25)
                .jsonPath("$.sectionContinuity").isEqualTo(25);

        assertThat(countPreferences(fixture.concertId())).isZero();
    }

    @Test
    void directorCanCreateAndReplacePreferences() {
        Fixture fixture = fixture("upsert");
        Session session = login(fixture.email());
        String path = path(fixture.choirId(), fixture.concertId());

        web.put()
                .uri(path)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "distance", 40,
                        "crossings", 30,
                        "compactness", 20,
                        "sectionContinuity", 10))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.distance").isEqualTo(40)
                .jsonPath("$.crossings").isEqualTo(30)
                .jsonPath("$.compactness").isEqualTo(20)
                .jsonPath("$.sectionContinuity").isEqualTo(10);

        assertThat(countPreferences(fixture.concertId())).isEqualTo(1);

        web.put()
                .uri(path)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "distance", 0,
                        "crossings", 50,
                        "compactness", 30,
                        "sectionContinuity", 20))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.distance").isEqualTo(0)
                .jsonPath("$.crossings").isEqualTo(50)
                .jsonPath("$.compactness").isEqualTo(30)
                .jsonPath("$.sectionContinuity").isEqualTo(20);

        assertThat(countPreferences(fixture.concertId())).isEqualTo(1);

        web.get()
                .uri(path)
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.distance").isEqualTo(0)
                .jsonPath("$.crossings").isEqualTo(50)
                .jsonPath("$.compactness").isEqualTo(30)
                .jsonPath("$.sectionContinuity").isEqualTo(20);
    }

    @Test
    void putRejectsWeightsThatDoNotSumToOneHundred() {
        Fixture fixture = fixture("invalid");
        Session session = login(fixture.email());

        web.put()
                .uri(path(fixture.choirId(), fixture.concertId()))
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "distance", 40,
                        "crossings", 30,
                        "compactness", 20,
                        "sectionContinuity", 5))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(countPreferences(fixture.concertId())).isZero();
    }

    @Test
    void directorCannotReadAnotherDirectorsPreferences() {
        Fixture owner = fixture("owner");
        Fixture other = fixture("other");
        Session otherSession = login(other.email());

        web.get()
                .uri(path(owner.choirId(), owner.concertId()))
                .cookie("SESSION", otherSession.id())
                .exchange()
                .expectStatus().isNotFound();

        web.get()
                .uri(path(other.choirId(), Long.MAX_VALUE))
                .cookie("SESSION", otherSession.id())
                .exchange()
                .expectStatus().isNotFound();
    }

    private Fixture fixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        String email = unique + "@example.test";

        jdbc.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                "Director " + unique,
                email,
                passwordEncoder.encode(PASSWORD));

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);
        directorIds.add(directorId);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                "Coro " + unique);

        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class,
                directorId);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """,
                choirId,
                "Concierto " + unique);

        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ?",
                Long.class,
                choirId);

        return new Fixture(email, choirId, concertId);
    }

    private Session login(String email) {
        var csrfResponse = web.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie initialCookie =
                csrfResponse.getResponseCookies().getFirst("SESSION");
        String csrf = csrfResponse.getResponseBody().get("token");

        var loginResponse = web.post()
                .uri("/api/auth/login")
                .cookie("SESSION", initialCookie.getValue())
                .header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticatedCookie =
                loginResponse.getResponseCookies().getFirst("SESSION");

        return new Session(authenticatedCookie.getValue(), csrf);
    }

    private int countPreferences(long concertId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM concert_optimization_preferences
                WHERE concert_id = ?
                """, Integer.class, concertId);
    }

    private String path(long choirId, long concertId) {
        return "/api/choirs/" + choirId
                + "/concerts/" + concertId
                + "/optimization-preferences";
    }

    private record Fixture(String email, Long choirId, Long concertId) {}
    private record Session(String id, String csrf) {}
}
