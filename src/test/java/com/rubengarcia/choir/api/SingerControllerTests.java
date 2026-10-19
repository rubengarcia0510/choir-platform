package com.rubengarcia.choir.api;

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
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SingerControllerTests {

    private static final String PASSWORD = "Singer-Test-Password-2026!";

    private final WebTestClient webTestClient;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    private Long directorId;
    private Long otherDirectorId;

    SingerControllerTests(
            WebTestClient webTestClient,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.webTestClient = webTestClient;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @AfterEach
    void cleanup() {
        cleanupDirector(directorId);
        cleanupDirector(otherDirectorId);
        directorId = null;
        otherDirectorId = null;
    }

    @Test
    void directorCanCreateUpdateDeactivateAndReactivateSingerWithoutChangingHistory()
            throws Exception {
        Director director = createDirector("Lifecycle");
        Session session = login(director.email());
        Long choirId = createChoir(session, "Coro lifecycle");
        Long sectionId = createSection(session, choirId, "Sopranos");

        String response = webTestClient.post()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "displayName", "  Ana Pérez  ",
                        "email", "ana@example.test",
                        "voicePart", "Soprano",
                        "voiceSectionId", sectionId,
                        "mobility", "  Acceso sin escaleras  ",
                        "restrictions", "  Evitar estar de pie mucho tiempo  "))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> created = objectMapper.readValue(
                response, new tools.jackson.core.type.TypeReference<>() {});
        Long singerId = ((Number) created.get("id")).longValue();

        assertThat(created.get("displayName")).isEqualTo("Ana Pérez");
        assertThat(created.get("mobility")).isEqualTo("Acceso sin escaleras");
        assertThat(created.get("restrictions"))
                .isEqualTo("Evitar estar de pie mucho tiempo");
        assertThat(created.get("active")).isEqualTo(true);

        jdbcTemplate.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concierto histórico");

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concierto histórico");
        assertNotNull(concertId);

        jdbcTemplate.update("""
                INSERT INTO concert_cast_snapshots
                    (concert_id, source_singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                concertId, singerId, "Ana Pérez", "Soprano",
                sectionId, "Sopranos", 1);

        webTestClient.put()
                .uri("/api/choirs/{choirId}/singers/{singerId}", choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "displayName", "Ana Pérez Gómez",
                        "email", "ana.gomez@example.test",
                        "voicePart", "Mezzosoprano",
                        "voiceSectionId", sectionId,
                        "mobility", "Sin restricciones",
                        "restrictions", "Ninguna"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.displayName").isEqualTo("Ana Pérez Gómez")
                .jsonPath("$.voicePart").isEqualTo("Mezzosoprano");

        webTestClient.delete()
                .uri("/api/choirs/{choirId}/singers/{singerId}", choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isEmpty();

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/choirs/{choirId}/singers")
                        .queryParam("includeInactive", true)
                        .build(choirId))
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].active").isEqualTo(false);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM concert_cast_snapshots
                WHERE concert_id = ? AND source_singer_id = ?
                  AND singer_name_snapshot = ?
                  AND voice_part_snapshot = ?
                  AND voice_section_name_snapshot = ?
                  AND division_number_snapshot = 1
                """, Integer.class,
                concertId, singerId, "Ana Pérez", "Soprano", "Sopranos"))
                .isEqualTo(1);

        webTestClient.post()
                .uri("/api/choirs/{choirId}/singers/{singerId}/reactivate",
                        choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true);

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers/{singerId}", choirId, singerId)
                .cookie("SESSION", session.sessionId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.displayName").isEqualTo("Ana Pérez Gómez")
                .jsonPath("$.active").isEqualTo(true);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM concert_cast_snapshots
                WHERE concert_id = ? AND singer_name_snapshot = 'Ana Pérez'
                """, Integer.class, concertId)).isEqualTo(1);
    }

    @Test
    void directorCannotReadOrModifyAnotherDirectorsSinger() throws Exception {
        Director owner = createDirector("Owner");
        Director other = createDirector("Other");
        Session ownerSession = login(owner.email());
        Session otherSession = login(other.email());

        Long choirId = createChoir(ownerSession, "Coro privado");
        Long singerId = createSinger(ownerSession, choirId, "Coreuta privado");

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", otherSession.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/api/choirs/{choirId}/singers/{singerId}", choirId, singerId)
                .cookie("SESSION", otherSession.sessionId())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.put()
                .uri("/api/choirs/{choirId}/singers/{singerId}", choirId, singerId)
                .cookie("SESSION", otherSession.sessionId())
                .header("X-CSRF-TOKEN", otherSession.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "displayName", "Modificado",
                        "email", "modificado@example.test",
                        "voicePart", "Tenor",
                        "mobility", "",
                        "restrictions", ""))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri("/api/choirs/{choirId}/singers/{singerId}", choirId, singerId)
                .cookie("SESSION", otherSession.sessionId())
                .header("X-CSRF-TOKEN", otherSession.csrfToken())
                .exchange()
                .expectStatus().isNotFound();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM singers WHERE id = ?",
                String.class, singerId)).isEqualTo("Coreuta privado");
    }

    private Long createChoir(Session session, String name) throws Exception {
        String response = webTestClient.post()
                .uri("/api/choirs")
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "description", ""))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> body = objectMapper.readValue(
                response, new tools.jackson.core.type.TypeReference<>() {});
        return ((Number) body.get("id")).longValue();
    }

    private Long createSection(Session session, Long choirId, String name) throws Exception {
        String response = webTestClient.post()
                .uri("/api/choirs/{choirId}/vocal-sections", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "sortOrder", 0))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> body = objectMapper.readValue(
                response, new tools.jackson.core.type.TypeReference<>() {});
        return ((Number) body.get("id")).longValue();
    }

    private Long createSinger(Session session, Long choirId, String name) throws Exception {
        String response = webTestClient.post()
                .uri("/api/choirs/{choirId}/singers", choirId)
                .cookie("SESSION", session.sessionId())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("displayName", name))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertNotNull(response);
        Map<String, Object> body = objectMapper.readValue(
                response, new tools.jackson.core.type.TypeReference<>() {});
        return ((Number) body.get("id")).longValue();
    }

    private Director createDirector(String label) {
        String unique = UUID.randomUUID().toString();
        String email = label.toLowerCase() + "-" + unique + "@example.test";
        String displayName = "Director " + label + " " + unique;

        jdbcTemplate.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """,
                displayName, email, passwordEncoder.encode(PASSWORD));

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        assertNotNull(id);

        if (directorId == null) {
            directorId = id;
        } else {
            otherDirectorId = id;
        }
        return new Director(id, email);
    }

    private Session login(String email) throws Exception {
        EntityExchangeResult<Map<String, String>> csrfResult = webTestClient.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie initialCookie = csrfResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(initialCookie);
        String csrfToken = csrfResult.getResponseBody().get("token");
        assertNotNull(csrfToken);

        EntityExchangeResult<String> loginResult = webTestClient.post()
                .uri("/api/auth/login")
                .cookie("SESSION", initialCookie.getValue())
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of(
                        "email", email, "password", PASSWORD)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticatedCookie =
                loginResult.getResponseCookies().getFirst("SESSION");
        assertNotNull(authenticatedCookie);
        return new Session(authenticatedCookie.getValue(), csrfToken);
    }

    private void cleanupDirector(Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("""
                DELETE FROM concert_cast_snapshots
                WHERE concert_id IN (SELECT id FROM concerts WHERE choir_id IN
                    (SELECT id FROM choirs WHERE director_id = ?))
                """, id);
        jdbcTemplate.update("""
                DELETE FROM concerts WHERE choir_id IN
                    (SELECT id FROM choirs WHERE director_id = ?)
                """, id);
        jdbcTemplate.update("""
                DELETE FROM singers WHERE choir_id IN
                    (SELECT id FROM choirs WHERE director_id = ?)
                """, id);
        jdbcTemplate.update("""
                DELETE FROM vocal_sections WHERE choir_id IN
                    (SELECT id FROM choirs WHERE director_id = ?)
                """, id);
        jdbcTemplate.update("DELETE FROM choirs WHERE director_id = ?", id);
        jdbcTemplate.update("DELETE FROM directors WHERE id = ?", id);
    }

    private record Director(Long id, String email) {}
    private record Session(String sessionId, String csrfToken) {}
}
