package com.rubengarcia.choir.api;

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
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureWebTestClient
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SceneRuleControllerTests {

    private static final String PASSWORD = "Scene-Rule-Test-Password-2026!";

    private final WebTestClient web;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final List<Long> directorIds = new ArrayList<>();

    SceneRuleControllerTests(
            WebTestClient web,
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.web = web;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @AfterEach
    void cleanup() {
        for (Long directorId : directorIds) {
            List<Long> choirIds = jdbc.queryForList(
                    "SELECT id FROM choirs WHERE director_id = ?",
                    Long.class, directorId);

            for (Long choirId : choirIds) {
                jdbc.update("""
                        DELETE FROM concert_repertoire
                        WHERE concert_id IN (
                            SELECT id FROM concerts WHERE choir_id = ?)
                        """, choirId);
                jdbc.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM singers WHERE choir_id = ?", choirId);
                jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
            }
            jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
        }
        directorIds.clear();
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        web.get()
                .uri(path(1L, 1L, 1L))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void directorCanManageRulesThroughHttp() throws Exception {
        Fixture fixture = fixture("crud");
        Session session = login(fixture.email());
        jdbc.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, 'Coreuta identificada', 'SOPRANO', TRUE)
                """, fixture.choirId());
        Long singerId = jdbc.queryForObject("""
                SELECT id FROM singers
                WHERE choir_id = ? AND display_name = 'Coreuta identificada'
                """, Long.class, fixture.choirId());

        String base = path(
                fixture.choirId(), fixture.concertId(), fixture.songId());

        web.post()
                .uri(base)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of(
                        "ruleType", "MOVEMENT",
                        "description", "Entrada ordenada",
                        "groups", List.of(Map.of(
                                "label", "Grupo A",
                                "compositionMode", "IDENTIFIED",
                                "singerIds", List.of(singerId))))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.order").isEqualTo(0)
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.groups[0].label").isEqualTo("Grupo A")
                .jsonPath("$.groups[0].compositionMode").isEqualTo("IDENTIFIED")
                .jsonPath("$.groups[0].memberCount").isEqualTo(1)
                .jsonPath("$.groups[0].singerIds[0]").isEqualTo(singerId);

        String secondBody = objectMapper.writeValueAsString(Map.of(
                "ruleType", "EXIT",
                "description", "Salida en dos grupos",
                "groups", List.of(
                        Map.of("label", "Izquierda", "memberCount", 4),
                        Map.of("label", "Derecha", "memberCount", 4))));

        String secondResponse = web.post()
                .uri(base)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(secondBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(secondResponse).isNotNull();
        Map<String, Object> second = objectMapper.readValue(
                secondResponse,
                new tools.jackson.core.type.TypeReference<>() {});
        long secondId = ((Number) second.get("id")).longValue();

        String listResponse = web.get()
                .uri(base)
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(listResponse).isNotNull();
        List<Map<String, Object>> rules = objectMapper.readValue(
                listResponse,
                new tools.jackson.core.type.TypeReference<>() {});
        assertThat(rules).hasSize(2);
        long firstId = ((Number) rules.get(0).get("id")).longValue();
        assertThat(((Number) rules.get(0).get("order")).intValue()).isZero();

        web.get()
                .uri(base + "/" + firstId)
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.description").isEqualTo("Entrada ordenada");

        web.put()
                .uri(base + "/" + firstId)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "ruleType", "CAST_DIVISION",
                        "description", "Dividir elenco",
                        "groups", List.of(
                                Map.of("label", "Grupo 1", "memberCount", 5),
                                Map.of("label", "Grupo 2", "memberCount", 3))))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ruleType").isEqualTo("CAST_DIVISION")
                .jsonPath("$.groups.length()").isEqualTo(2);

        web.put()
                .uri(base + "/order")
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("ruleIds", List.of(secondId, firstId)))
                .exchange()
                .expectStatus().isNoContent();

        web.get()
                .uri(base)
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(secondId)
                .jsonPath("$[0].order").isEqualTo(0)
                .jsonPath("$[1].id").isEqualTo(firstId)
                .jsonPath("$[1].order").isEqualTo(1);

        web.delete()
                .uri(base + "/" + firstId)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .exchange()
                .expectStatus().isNoContent();

        web.get()
                .uri(base + "/" + firstId)
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(false);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rules WHERE id = ?",
                Integer.class, firstId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rule_groups WHERE scene_rule_id = ?",
                Integer.class, firstId)).isEqualTo(2);
    }

    @Test
    void directorCannotManageAnotherDirectorsRules() throws Exception {
        Fixture owner = fixture("owner");
        Fixture other = fixture("other");
        Session ownerSession = login(owner.email());
        Session otherSession = login(other.email());

        String base = path(owner.choirId(), owner.concertId(), owner.songId());

        web.post()
                .uri(base)
                .cookie("SESSION", ownerSession.id())
                .header("X-CSRF-TOKEN", ownerSession.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "ruleType", "EXIT",
                        "description", "Salida",
                        "groups", List.of()))
                .exchange()
                .expectStatus().isCreated();

        web.get()
                .uri(base)
                .cookie("SESSION", otherSession.id())
                .exchange()
                .expectStatus().isNotFound();

        web.post()
                .uri(path(other.choirId(), other.concertId(), owner.songId()))
                .cookie("SESSION", otherSession.id())
                .header("X-CSRF-TOKEN", otherSession.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "ruleType", "EXIT",
                        "description", "No autorizada",
                        "groups", List.of()))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void invalidRuleRequestIsRejected() throws Exception {
        Fixture fixture = fixture("invalid");
        Session session = login(fixture.email());

        web.post()
                .uri(path(fixture.choirId(), fixture.concertId(), fixture.songId()))
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of(
                        "ruleType", "UNKNOWN",
                        "description", "Regla inválida",
                        "groups", List.of())))
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM scene_rules WHERE repertoire_item_id = ?",
                Integer.class, fixture.songId())).isZero();
    }

    private Fixture fixture(String suffix) {
        String unique = suffix + "-" + UUID.randomUUID();
        String email = unique + "@example.test";

        jdbc.update("""
                INSERT INTO directors
                    (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """, "Director " + unique, email, passwordEncoder.encode(PASSWORD));

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        assertThat(directorId).isNotNull();
        directorIds.add(directorId);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro " + unique);
        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro " + unique);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Concierto " + unique);
        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concierto " + unique);

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra " + unique);
        Long songId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Obra " + unique);

        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, songId);

        return new Fixture(email, choirId, concertId, songId);
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
        assertThat(initialCookie).isNotNull();
        String csrf = csrfResponse.getResponseBody().get("token");
        assertThat(csrf).isNotBlank();

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
        assertThat(authenticatedCookie).isNotNull();

        return new Session(authenticatedCookie.getValue(), csrf);
    }

    private String path(long choirId, long concertId, long songId) {
        return "/api/choirs/" + choirId + "/concerts/" + concertId
                + "/repertoire/" + songId + "/scene-rules";
    }

    private record Fixture(String email, Long choirId, Long concertId, Long songId) {}
    private record Session(String id, String csrf) {}
}
