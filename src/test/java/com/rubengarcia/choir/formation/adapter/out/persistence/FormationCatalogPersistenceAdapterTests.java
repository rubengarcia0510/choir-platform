package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort.DraftDetails;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort.PositionUpdate;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FormationCatalogPersistenceAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private FormationCatalogPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> directorIds = new ArrayList<>();

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @AfterEach
    void cleanUp() {
        for (Long directorId : directorIds) {
            deleteDirectorData(directorId);
        }
        directorIds.clear();
    }

    @Test
    void replacesAllDraftPositionsAndPersistsFreshSnapshots() {
        Long firstSingerId = fixture.singerId();
        Long secondSingerId = insertSinger(
                fixture.choirId(),
                "Cantante nuevo " + fixture.suffix(),
                "ALTO",
                true);

        List<PositionUpdate> updates = List.of(
                new PositionUpdate(
                        firstSingerId,
                        "  Izquierda  ",
                        new BigDecimal("15.000"),
                        new BigDecimal("25.000")),
                new PositionUpdate(
                        secondSingerId,
                        "Derecha",
                        new BigDecimal("75.000"),
                        new BigDecimal("65.000")));

        DraftDetails result = adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        updates)
                .block(TIMEOUT);

        assertThat(result).isNotNull();
        assertThat(result.positions()).hasSize(2);

        assertThat(result.positions())
                .extracting(position -> position.getSingerId())
                .containsExactlyInAnyOrder(firstSingerId, secondSingerId);

        assertThat(result.positions())
                .extracting(position -> position.getPositionLabel())
                .containsExactlyInAnyOrder("Izquierda", "Derecha");

        assertThat(result.positions())
                .filteredOn(position -> position.getSingerId().equals(firstSingerId))
                .singleElement()
                .satisfies(position -> {
                    assertThat(position.getSingerNameSnapshot())
                            .isEqualTo(fixture.singerName());
                    assertThat(position.getVoicePartSnapshot())
                            .isEqualTo("SOPRANO");
                    assertThat(position.getXCoordinate())
                            .isEqualByComparingTo("15.000");
                    assertThat(position.getYCoordinate())
                            .isEqualByComparingTo("25.000");
                });

        assertThat(countPositions(fixture.versionId())).isEqualTo(2);
        assertThat(countPositionsForSinger(
                fixture.versionId(), secondSingerId)).isEqualTo(1);
    }

    @Test
    void publishedVersionRejectsReplacementAndPreservesExistingPositions() {
        jdbcTemplate.update(
                "UPDATE formation_versions "
                        + "SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                fixture.versionId());

        assertThatThrownBy(() -> adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(new PositionUpdate(
                                fixture.singerId(),
                                "Nueva posición",
                                new BigDecimal("80"),
                                new BigDecimal("80"))))
                .block(TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no es editable");

        assertOriginalPositionRemains();
    }

    @Test
    void incorrectConcertRepertoireAssociationDoesNotChangeFormationPositions() {
        Long anotherRepertoireItemId = insertRepertoireItem(
                fixture.choirId(),
                "Otra obra " + fixture.suffix());

        jdbcTemplate.update(
                "INSERT INTO concert_repertoire "
                        + "(concert_id, repertoire_item_id, order_index) "
                        + "VALUES (?, ?, ?)",
                fixture.concertId(),
                anotherRepertoireItemId,
                1);

        DraftDetails result = adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        anotherRepertoireItemId,
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(new PositionUpdate(
                                fixture.singerId(),
                                "Posición indebida",
                                new BigDecimal("90"),
                                new BigDecimal("90"))))
                .block(TIMEOUT);

        assertThat(result).isNull();
        assertOriginalPositionRemains();
    }

    @Test
    void inactiveSingerIsRejectedWithoutDeletingExistingPositions() {
        jdbcTemplate.update(
                "UPDATE singers SET active = FALSE WHERE id = ?",
                fixture.singerId());

        assertThatThrownBy(() -> adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(new PositionUpdate(
                                fixture.singerId(),
                                "Posición inválida",
                                new BigDecimal("40"),
                                new BigDecimal("40"))))
                .block(TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe, está inactivo");

        assertOriginalPositionRemains();
    }

    @Test
    void singerFromAnotherChoirIsRejectedWithoutDeletingExistingPositions() {
        Long anotherChoirId = insertChoir(
                fixture.directorId(),
                "Otro coro " + fixture.suffix());

        Long foreignSingerId = insertSinger(
                anotherChoirId,
                "Cantante ajeno " + fixture.suffix(),
                "TENOR",
                true);

        assertThatThrownBy(() -> adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(new PositionUpdate(
                                foreignSingerId,
                                "Posición de otro coro",
                                new BigDecimal("50"),
                                new BigDecimal("50"))))
                .block(TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe, está inactivo");

        assertOriginalPositionRemains();
    }


    @Test
    void failedInsertRollsBackDeletedPositions() {
        String invalidLabel = "X".repeat(81);

        assertThatThrownBy(() -> adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(new PositionUpdate(
                                fixture.singerId(),
                                invalidLabel,
                                new BigDecimal("40"),
                                new BigDecimal("40"))))
                .block(TIMEOUT))
                .isInstanceOf(RuntimeException.class);

        assertOriginalPositionRemains();
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director de prueba " + suffix,
                "formation-persistence-" + suffix + "@example.test");

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                "formation-persistence-" + suffix + "@example.test");

        directorIds.add(directorId);

        Long choirId = insertChoir(
                directorId,
                "Coro de prueba " + suffix);

        Long singerId = insertSinger(
                choirId,
                "Cantante original " + suffix,
                "SOPRANO",
                true);

        Long concertId = insertConcert(
                choirId,
                "Concierto de prueba " + suffix);

        Long repertoireItemId = insertRepertoireItem(
                choirId,
                "Obra de prueba " + suffix);

        jdbcTemplate.update(
                "INSERT INTO concert_repertoire "
                        + "(concert_id, repertoire_item_id, order_index) "
                        + "VALUES (?, ?, ?)",
                concertId,
                repertoireItemId,
                0);

        jdbcTemplate.update(
                "INSERT INTO formations "
                        + "(choir_id, concert_id, repertoire_item_id, name, description) "
                        + "VALUES (?, ?, ?, ?, ?)",
                choirId,
                concertId,
                repertoireItemId,
                "Formación inicial " + suffix,
                "Fixture de persistencia");

        Long formationId = jdbcTemplate.queryForObject(
                "SELECT id FROM formations "
                        + "WHERE choir_id = ? AND concert_id = ? "
                        + "AND repertoire_item_id = ? AND name = ?",
                Long.class,
                choirId,
                concertId,
                repertoireItemId,
                "Formación inicial " + suffix);

        jdbcTemplate.update(
                "INSERT INTO formation_versions "
                        + "(formation_id, version_number, status) "
                        + "VALUES (?, ?, ?)",
                formationId,
                1,
                "DRAFT");

        Long versionId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions "
                        + "WHERE formation_id = ? AND version_number = ?",
                Long.class,
                formationId,
                1);

        jdbcTemplate.update(
                "INSERT INTO formation_positions "
                        + "(formation_version_id, singer_id, "
                        + "singer_name_snapshot, voice_part_snapshot, "
                        + "position_label, x_coordinate, y_coordinate) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                versionId,
                singerId,
                "Snapshot anterior",
                "SOPRANO",
                "Posición anterior",
                new BigDecimal("10.000"),
                new BigDecimal("20.000"));

        return new Fixture(
                suffix,
                directorId,
                choirId,
                singerId,
                "Cantante original " + suffix,
                concertId,
                repertoireItemId,
                formationId,
                versionId);
    }

    private Long insertChoir(Long directorId, String name) {
        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                name);
    }

    private Long insertSinger(
            Long choirId,
            String displayName,
            String voicePart,
            boolean active) {
        jdbcTemplate.update(
                "INSERT INTO singers "
                        + "(choir_id, display_name, voice_part, active) "
                        + "VALUES (?, ?, ?, ?)",
                choirId,
                displayName,
                voicePart,
                active);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                displayName);
    }

    private Long insertConcert(Long choirId, String title) {
        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                title);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                title);
    }

    private Long insertRepertoireItem(Long choirId, String title) {
        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                title);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                title);
    }

    private void assertOriginalPositionRemains() {
        assertThat(countPositions(fixture.versionId())).isEqualTo(1);

        String label = jdbcTemplate.queryForObject(
                "SELECT position_label FROM formation_positions "
                        + "WHERE formation_version_id = ?",
                String.class,
                fixture.versionId());

        String snapshotName = jdbcTemplate.queryForObject(
                "SELECT singer_name_snapshot FROM formation_positions "
                        + "WHERE formation_version_id = ?",
                String.class,
                fixture.versionId());

        assertThat(label).isEqualTo("Posición anterior");
        assertThat(snapshotName).isEqualTo("Snapshot anterior");
    }

    private int countPositions(Long versionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formation_positions "
                        + "WHERE formation_version_id = ?",
                Integer.class,
                versionId);
    }

    private int countPositionsForSinger(Long versionId, Long singerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formation_positions "
                        + "WHERE formation_version_id = ? AND singer_id = ?",
                Integer.class,
                versionId,
                singerId);
    }

    private void deleteDirectorData(Long directorId) {
        List<Long> choirIds = jdbcTemplate.queryForList(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class,
                directorId);

        for (Long choirId : choirIds) {
            jdbcTemplate.update(
                    "DELETE FROM formation_positions "
                            + "WHERE formation_version_id IN "
                            + "(SELECT fv.id FROM formation_versions fv "
                            + "JOIN formations f ON f.id = fv.formation_id "
                            + "WHERE f.choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM transition_versions "
                            + "WHERE transition_id IN "
                            + "(SELECT id FROM transitions WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM formation_versions "
                            + "WHERE formation_id IN "
                            + "(SELECT id FROM formations WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM formations WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concert_vocal_adjustments WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concert_repertoire "
                            + "WHERE concert_id IN "
                            + "(SELECT id FROM concerts WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concert_cast_snapshots WHERE concert_id IN "
                            + "(SELECT id FROM concerts WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM absences WHERE singer_id IN "
                            + "(SELECT id FROM singers WHERE choir_id = ?)",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM concerts WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM vocal_plans WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM singers WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM transitions WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?",
                    choirId);

            jdbcTemplate.update(
                    "DELETE FROM choirs WHERE id = ?",
                    choirId);
        }

        jdbcTemplate.update(
                "DELETE FROM directors WHERE id = ?",
                directorId);
    }

    private record Fixture(
            String suffix,
            Long directorId,
            Long choirId,
            Long singerId,
            String singerName,
            Long concertId,
            Long repertoireItemId,
            Long formationId,
            Long versionId) {}
}
