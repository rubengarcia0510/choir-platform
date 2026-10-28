package com.rubengarcia.choir.service;

import com.rubengarcia.choir.scene.application.port.out.SceneRuleHistoryPort;
import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SceneRuleHistoryAggregateOrderingTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SceneRuleService sceneRuleService;

    @Autowired
    private SceneRuleHistoryPort sceneRuleHistoryPort;

    private final List<Long> createdDirectorIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            List<Long> choirIds = jdbc.queryForList(
                    "SELECT id FROM choirs WHERE director_id = ?",
                    Long.class,
                    directorId);

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

        createdDirectorIds.clear();
    }

    @Test
    void preservesRevisionGroupAndMemberOrderingInHistoricalAggregate() {
        Fixture fixture = createFixture();

        long singerA = createSinger(fixture, "Coreuta A");
        long singerB = createSinger(fixture, "Coreuta B");
        long singerC = createSinger(fixture, "Coreuta C");
        long singerD = createSinger(fixture, "Coreuta D");

        SceneRuleService.RuleView created = sceneRuleService.create(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Distribución inicial",
                        List.of(
                                identifiedGroup("Primero", singerB, singerA),
                                identifiedGroup("Segundo", singerD, singerC))))
                .block(Duration.ofSeconds(10));

        sceneRuleService.update(
                fixture.email(),
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                created.id(),
                new SceneRuleService.RuleData(
                        "MOVEMENT",
                        "Distribución actualizada",
                        List.of(
                                identifiedGroup("Nuevo primero", singerC, singerB),
                                identifiedGroup("Nuevo segundo", singerA, singerD))))
                .block(Duration.ofSeconds(10));

        List<SceneRuleRevisionSnapshot> history = sceneRuleHistoryPort
                .findAllRevisionsByRepertoireItemId(fixture.repertoireItemId())
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(history).isNotNull();
        assertThat(history)
                .filteredOn(snapshot -> snapshot.sceneRuleId() == created.id())
                .extracting(SceneRuleRevisionSnapshot::revisionNumber)
                .containsExactly(1, 2);

        SceneRuleRevisionSnapshot firstRevision = history.stream()
                .filter(snapshot -> snapshot.sceneRuleId() == created.id())
                .filter(snapshot -> snapshot.revisionNumber() == 1)
                .findFirst()
                .orElseThrow();

        SceneRuleRevisionSnapshot secondRevision = history.stream()
                .filter(snapshot -> snapshot.sceneRuleId() == created.id())
                .filter(snapshot -> snapshot.revisionNumber() == 2)
                .findFirst()
                .orElseThrow();

        assertThat(firstRevision.groups())
                .extracting(SceneRuleRevisionSnapshot.GroupSnapshot::label)
                .containsExactly("Primero", "Segundo");

        assertThat(firstRevision.groups())
                .extracting(SceneRuleRevisionSnapshot.GroupSnapshot::singerIds)
                .containsExactly(
                        List.of(singerB, singerA),
                        List.of(singerD, singerC));

        assertThat(secondRevision.groups())
                .extracting(SceneRuleRevisionSnapshot.GroupSnapshot::label)
                .containsExactly("Nuevo primero", "Nuevo segundo");

        assertThat(secondRevision.groups())
                .extracting(SceneRuleRevisionSnapshot.GroupSnapshot::singerIds)
                .containsExactly(
                        List.of(singerC, singerB),
                        List.of(singerA, singerD));

        assertThat(firstRevision.groups())
                .allSatisfy(group ->
                        assertThat(group.compositionMode()).isEqualTo("IDENTIFIED"));
        assertThat(secondRevision.groups())
                .allSatisfy(group ->
                        assertThat(group.compositionMode()).isEqualTo("IDENTIFIED"));
    }

    private SceneRuleService.GroupData identifiedGroup(
            String label,
            long firstSingerId,
            long secondSingerId) {
        return new SceneRuleService.GroupData(
                label,
                "IDENTIFIED",
                null,
                List.of(firstSingerId, secondSingerId));
    }

    private long createSinger(Fixture fixture, String name) {
        jdbc.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'SOPRANO', TRUE)
                """, fixture.choirId(), name);

        return jdbc.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                fixture.choirId(),
                name);
    }

    private Fixture createFixture() {
        String suffix = "history-order-" + UUID.randomUUID();
        String email = suffix + "@example.test";

        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director " + suffix,
                email);

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);
        createdDirectorIds.add(directorId);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                "Coro " + suffix);

        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                "Coro " + suffix);

        jdbc.update(
                "INSERT INTO concerts (choir_id, title, status) VALUES (?, ?, 'PLANNED')",
                choirId,
                "Concierto " + suffix);

        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Concierto " + suffix);

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                "Obra " + suffix);

        Long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Obra " + suffix);

        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        return new Fixture(email, choirId, concertId, repertoireItemId);
    }

    private record Fixture(
            String email,
            Long choirId,
            Long concertId,
            Long repertoireItemId) {
    }
}
