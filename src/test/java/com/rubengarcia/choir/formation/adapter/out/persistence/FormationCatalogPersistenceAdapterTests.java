package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort.DraftDetails;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort.PositionUpdate;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
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
    private FormationDuplicationPort duplicationPort;

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
        insertAssignment(
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                secondSingerId,
                fixture.divisionId());

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
    void persistenceFailureAfterDeleteRollsBackAndPreservesOriginalPositions() {
        jdbcTemplate.execute(
                "ALTER TABLE formation_positions "
                        + "ADD CONSTRAINT choir71_force_failure "
                        + "CHECK (position_label IS NULL "
                        + "OR position_label <> 'FORCE_PERSISTENCE_FAILURE')");

        try {
            assertThatThrownBy(() -> adapter.replaceDraftPositions(
                            fixture.choirId(),
                            fixture.concertId(),
                            fixture.repertoireItemId(),
                            fixture.formationId(),
                            fixture.versionId(),
                            List.of(new PositionUpdate(
                                    fixture.singerId(),
                                    "FORCE_PERSISTENCE_FAILURE",
                                    new BigDecimal("80"),
                                    new BigDecimal("80"))))
                    .block(TIMEOUT))
                    .isNotNull();

            // El DELETE ocurrió antes del INSERT fallido: la fila original
            // debe seguir existiendo si la operación fue transaccional.
            assertOriginalPositionRemains();
        } finally {
            jdbcTemplate.execute(
                    "ALTER TABLE formation_positions "
                            + "DROP CONSTRAINT choir71_force_failure");
        }
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
    void archivedVersionRejectsReplacementAndPreservesExistingPositions() {
        jdbcTemplate.update(
                "UPDATE formation_versions SET status = 'ARCHIVED' WHERE id = ?",
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
    void versionFromAnotherFormationIsRejectedWithoutDeletingExistingPositions() {
        String otherFormationName = "Otra formación " + fixture.suffix();

        jdbcTemplate.update(
                "INSERT INTO formations "
                        + "(choir_id, concert_id, repertoire_item_id, name, description) "
                        + "VALUES (?, ?, ?, ?, ?)",
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                otherFormationName,
                "Fixture de versión ajena");

        Long otherFormationId = jdbcTemplate.queryForObject(
                "SELECT id FROM formations "
                        + "WHERE choir_id = ? AND concert_id = ? "
                        + "AND repertoire_item_id = ? AND name = ?",
                Long.class,
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                otherFormationName);

        jdbcTemplate.update(
                "INSERT INTO formation_versions (formation_id, version_number, status) "
                        + "VALUES (?, ?, ?)",
                otherFormationId,
                1,
                "DRAFT");

        Long otherVersionId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions "
                        + "WHERE formation_id = ? AND version_number = ?",
                Long.class,
                otherFormationId,
                1);

        assertThat(adapter.replaceDraftPositions(
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                fixture.formationId(),
                otherVersionId,
                List.of(new PositionUpdate(
                        fixture.singerId(),
                        "Posición cruzada",
                        new BigDecimal("80"),
                        new BigDecimal("80"))))
                .block(TIMEOUT)).isNull();

        assertOriginalPositionRemains();
        assertThat(countPositions(otherVersionId)).isZero();
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
    void sameWorkInAnotherConcertCannotReplaceFormationPositions() {
        Long anotherConcertId = insertConcert(
                fixture.choirId(),
                "Otro concierto " + fixture.suffix());

        jdbcTemplate.update(
                "INSERT INTO concert_repertoire "
                        + "(concert_id, repertoire_item_id, order_index) "
                        + "VALUES (?, ?, ?)",
                anotherConcertId,
                fixture.repertoireItemId(),
                0);

        DraftDetails result = adapter.replaceDraftPositions(
                        fixture.choirId(),
                        anotherConcertId,
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(new PositionUpdate(
                                fixture.singerId(),
                                "Posición cruzada",
                                new BigDecimal("90"),
                                new BigDecimal("90"))))
                .block(TIMEOUT);

        assertThat(result).isNull();
        assertOriginalPositionRemains();
    }

    @Test
    void singerAssignedToAnotherWorkIsRejectedWithoutDeletingExistingPositions() {
        Long unassignedSingerId = insertSinger(
                fixture.choirId(),
                "Cantante asignado a otra obra " + fixture.suffix(),
                "ALTO",
                true);

        Long anotherRepertoireItemId = insertRepertoireItem(
                fixture.choirId(),
                "Obra distinta " + fixture.suffix());

        jdbcTemplate.update(
                "INSERT INTO concert_repertoire "
                        + "(concert_id, repertoire_item_id, order_index) "
                        + "VALUES (?, ?, ?)",
                fixture.concertId(),
                anotherRepertoireItemId,
                1);

        Long anotherDivisionId = insertVocalPlanDivision(
                fixture.choirId(),
                anotherRepertoireItemId,
                fixture.vocalSectionId());

        insertAssignment(
                fixture.choirId(),
                fixture.concertId(),
                anotherRepertoireItemId,
                unassignedSingerId,
                anotherDivisionId);

        assertThatThrownBy(() -> adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(
                                new PositionUpdate(
                                        unassignedSingerId,
                                        "Asignación incorrecta",
                                        new BigDecimal("40"),
                                        new BigDecimal("40"))))
                .block(TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe, está inactivo");

        assertOriginalPositionRemains();
    }

    @Test
    void duplicateSingerIsRejectedWithoutDeletingExistingPositions() {
        assertThatThrownBy(() -> adapter.replaceDraftPositions(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        List.of(
                                new PositionUpdate(
                                        fixture.singerId(),
                                        "Primera posición",
                                        new BigDecimal("20"),
                                        new BigDecimal("20")),
                                new PositionUpdate(
                                        fixture.singerId(),
                                        "Posición duplicada",
                                        new BigDecimal("80"),
                                        new BigDecimal("80"))))
                .block(TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cantantes duplicados");

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



    @Test
    void duplicationFailureRollsBackFormationAndVersion() {
        int formationsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                Integer.class,
                fixture.choirId());

        int versionsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formation_versions",
                Integer.class);

        jdbcTemplate.execute(
                "ALTER TABLE formation_positions "
                        + "ADD CONSTRAINT choir72_force_duplicate_failure "
                        + "CHECK (formation_version_id <= "
                        + fixture.versionId() + ")");

        try {
            assertThatThrownBy(() -> duplicationPort.duplicate(
                            fixture.choirId(),
                            fixture.concertId(),
                            fixture.repertoireItemId(),
                            fixture.formationId(),
                            null,
                            "Copia que debe fallar " + fixture.suffix(),
                            "Prueba de rollback")
                    .block(TIMEOUT))
                    .isNotNull();

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                    Integer.class,
                    fixture.choirId()))
                    .isEqualTo(formationsBefore);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM formation_versions",
                    Integer.class))
                    .isEqualTo(versionsBefore);

            assertOriginalPositionRemains();
        } finally {
            jdbcTemplate.execute(
                    "ALTER TABLE formation_positions "
                            + "DROP CONSTRAINT choir72_force_duplicate_failure");
        }
    }

    @Test
    void duplicatesDraftAndKeepsPositionsIndependent() {
        jdbcTemplate.update(
                "UPDATE formation_versions "
                        + "SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                fixture.versionId());

        FormationDuplicationPort.CreatedFormation duplicated = duplicationPort.duplicate(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        null,
                        "Copia " + fixture.suffix(),
                        "Duplicación de prueba")
                .block(TIMEOUT);

        assertThat(duplicated).isNotNull();
        assertThat(duplicated.formation().getId()).isNotEqualTo(fixture.formationId());
        assertThat(duplicated.formation().getChoirId()).isEqualTo(fixture.choirId());
        assertThat(duplicated.formation().getConcertId()).isEqualTo(fixture.concertId());
        assertThat(duplicated.formation().getRepertoireItemId())
                .isEqualTo(fixture.repertoireItemId());
        assertThat(duplicated.draftVersion().getId()).isNotEqualTo(fixture.versionId());
        assertThat(duplicated.draftVersion().getVersionNumber()).isEqualTo(1);
        assertThat(duplicated.draftVersion().getStatus()).isEqualTo("DRAFT");

        Long copiedPositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_positions WHERE formation_version_id = ?",
                Long.class,
                duplicated.draftVersion().getId());

        assertThat(copiedPositionId).isNotNull();

        Long originalPositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_positions WHERE formation_version_id = ?",
                Long.class,
                fixture.versionId());

        assertThat(copiedPositionId).isNotEqualTo(originalPositionId);

        Map<String, Object> copiedPosition = jdbcTemplate.queryForMap(
                "SELECT singer_id, singer_name_snapshot, voice_part_snapshot, "
                        + "position_label, x_coordinate, y_coordinate "
                        + "FROM formation_positions WHERE id = ?",
                copiedPositionId);

        assertThat(copiedPosition.get("singer_id")).isEqualTo(fixture.singerId());
        assertThat(copiedPosition.get("singer_name_snapshot"))
                .isEqualTo("Snapshot anterior");
        assertThat(copiedPosition.get("voice_part_snapshot")).isEqualTo("SOPRANO");
        assertThat(copiedPosition.get("position_label")).isEqualTo("Posición anterior");
        assertThat((BigDecimal) copiedPosition.get("x_coordinate"))
                .isEqualByComparingTo("10.000");
        assertThat((BigDecimal) copiedPosition.get("y_coordinate"))
                .isEqualByComparingTo("20.000");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM formation_positions WHERE formation_version_id = ?",
                Integer.class,
                fixture.versionId())).isEqualTo(1);
    }

    @Test
    void explicitlyDuplicatesPublishedVersion() {
        jdbcTemplate.update(
                "UPDATE formation_versions "
                        + "SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                fixture.versionId());

        FormationDuplicationPort.CreatedFormation duplicated = duplicationPort.duplicate(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.versionId(),
                        "Copia publicada " + fixture.suffix(),
                        "Copia de una versión publicada")
                .block(TIMEOUT);

        assertThat(duplicated).isNotNull();
        assertThat(duplicated.formation().getId()).isNotEqualTo(fixture.formationId());
        assertThat(duplicated.draftVersion().getId()).isNotEqualTo(fixture.versionId());
        assertThat(duplicated.draftVersion().getStatus()).isEqualTo("DRAFT");

        Map<String, Object> copiedPosition = jdbcTemplate.queryForMap(
                "SELECT singer_name_snapshot, position_label "
                        + "FROM formation_positions WHERE formation_version_id = ?",
                duplicated.draftVersion().getId());

        assertThat(copiedPosition.get("singer_name_snapshot"))
                .isEqualTo("Snapshot anterior");
        assertThat(copiedPosition.get("position_label")).isEqualTo("Posición anterior");
    }


    @Test
    void duplicationSelectsHighestNumberedPublishedVersionEvenWhenNewerDraftExists() {
        jdbcTemplate.update(
                "UPDATE formation_versions "
                        + "SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                fixture.versionId());

        jdbcTemplate.update(
                "INSERT INTO formation_versions (formation_id, version_number, status) "
                        + "VALUES (?, ?, ?)",
                fixture.formationId(),
                2,
                "PUBLISHED");

        Long newerPublishedId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions "
                        + "WHERE formation_id = ? AND version_number = ?",
                Long.class,
                fixture.formationId(),
                2);

        jdbcTemplate.update(
                "UPDATE formation_versions "
                        + "SET published_at = CURRENT_TIMESTAMP WHERE id = ?",
                newerPublishedId);

        jdbcTemplate.update(
                "INSERT INTO formation_positions "
                        + "(formation_version_id, singer_id, singer_name_snapshot, "
                        + "voice_part_snapshot, position_label, x_coordinate, y_coordinate) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                newerPublishedId,
                fixture.singerId(),
                "Snapshot de la versión publicada más reciente",
                "ALTO",
                "Posición publicada más reciente",
                new BigDecimal("70"),
                new BigDecimal("80"));

        jdbcTemplate.update(
                "INSERT INTO formation_versions (formation_id, version_number, status) "
                        + "VALUES (?, ?, ?)",
                fixture.formationId(),
                3,
                "DRAFT");

        Long newerDraftId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions "
                        + "WHERE formation_id = ? AND version_number = ?",
                Long.class,
                fixture.formationId(),
                3);

        jdbcTemplate.update(
                "INSERT INTO formation_positions "
                        + "(formation_version_id, singer_id, singer_name_snapshot, "
                        + "voice_part_snapshot, position_label, x_coordinate, y_coordinate) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                newerDraftId,
                fixture.singerId(),
                "Snapshot del DRAFT que no debe elegirse",
                "BASS",
                "Posición del DRAFT posterior",
                new BigDecimal("90"),
                new BigDecimal("95"));

        FormationDuplicationPort.CreatedFormation duplicated = duplicationPort.duplicate(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        null,
                        "Copia de versiones publicadas y borradores " + fixture.suffix(),
                        "Debe elegir la versión publicada de mayor número")
                .block(TIMEOUT);

        assertThat(duplicated).isNotNull();

        Map<String, Object> copiedPosition = jdbcTemplate.queryForMap(
                "SELECT singer_name_snapshot, voice_part_snapshot, position_label "
                        + "FROM formation_positions WHERE formation_version_id = ?",
                duplicated.draftVersion().getId());

        assertThat(copiedPosition.get("singer_name_snapshot"))
                .isEqualTo("Snapshot de la versión publicada más reciente");
        assertThat(copiedPosition.get("voice_part_snapshot")).isEqualTo("ALTO");
        assertThat(copiedPosition.get("position_label"))
                .isEqualTo("Posición publicada más reciente");
    }

    @Test
    void duplicationFallsBackToLatestPublishedAndIgnoresArchivedVersions() {
        jdbcTemplate.update(
                "UPDATE formation_versions SET status = 'ARCHIVED' WHERE id = ?",
                fixture.versionId());

        jdbcTemplate.update(
                "INSERT INTO formation_versions "
                        + "(formation_id, version_number, status) VALUES (?, ?, ?)",
                fixture.formationId(),
                2,
                "PUBLISHED");

        Long olderPublishedId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions "
                        + "WHERE formation_id = ? AND version_number = ?",
                Long.class,
                fixture.formationId(),
                2);

        jdbcTemplate.update(
                "UPDATE formation_versions SET published_at = CURRENT_TIMESTAMP WHERE id = ?",
                olderPublishedId);

        jdbcTemplate.update(
                "INSERT INTO formation_positions "
                        + "(formation_version_id, singer_id, singer_name_snapshot, "
                        + "voice_part_snapshot, position_label, x_coordinate, y_coordinate) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                olderPublishedId,
                fixture.singerId(),
                "Snapshot publicado anterior",
                "TENOR",
                "Posición publicada anterior",
                new BigDecimal("20"),
                new BigDecimal("30"));

        jdbcTemplate.update(
                "INSERT INTO formation_versions "
                        + "(formation_id, version_number, status) VALUES (?, ?, ?)",
                fixture.formationId(),
                3,
                "PUBLISHED");

        Long latestPublishedId = jdbcTemplate.queryForObject(
                "SELECT id FROM formation_versions "
                        + "WHERE formation_id = ? AND version_number = ?",
                Long.class,
                fixture.formationId(),
                3);

        jdbcTemplate.update(
                "UPDATE formation_versions SET published_at = CURRENT_TIMESTAMP WHERE id = ?",
                latestPublishedId);

        jdbcTemplate.update(
                "INSERT INTO formation_positions "
                        + "(formation_version_id, singer_id, singer_name_snapshot, "
                        + "voice_part_snapshot, position_label, x_coordinate, y_coordinate) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                latestPublishedId,
                fixture.singerId(),
                "Snapshot publicado más reciente",
                "BASS",
                "Posición publicada más reciente",
                new BigDecimal("60"),
                new BigDecimal("70"));

        FormationDuplicationPort.CreatedFormation duplicated = duplicationPort.duplicate(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        null,
                        "Copia sin DRAFT " + fixture.suffix(),
                        "Debe elegir la versión publicada más reciente")
                .block(TIMEOUT);

        assertThat(duplicated).isNotNull();

        Map<String, Object> copiedPosition = jdbcTemplate.queryForMap(
                "SELECT singer_name_snapshot, voice_part_snapshot, position_label "
                        + "FROM formation_positions WHERE formation_version_id = ?",
                duplicated.draftVersion().getId());

        assertThat(copiedPosition.get("singer_name_snapshot"))
                .isEqualTo("Snapshot publicado más reciente");
        assertThat(copiedPosition.get("voice_part_snapshot")).isEqualTo("BASS");
        assertThat(copiedPosition.get("position_label"))
                .isEqualTo("Posición publicada más reciente");
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
                "INSERT INTO vocal_sections (choir_id, name, sort_order) "
                        + "VALUES (?, ?, ?)",
                choirId,
                "Sección de prueba " + suffix,
                0);
        Long vocalSectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                choirId,
                "Sección de prueba " + suffix);

        Long divisionId = insertVocalPlanDivision(
                choirId, repertoireItemId, vocalSectionId);
        insertAssignment(
                choirId, concertId, repertoireItemId, singerId, divisionId);

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
                versionId,
                vocalSectionId,
                divisionId);
    }

    private Long insertVocalPlanDivision(
            Long choirId,
            Long repertoireItemId,
            Long vocalSectionId) {
        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) "
                        + "VALUES (?, ?)",
                repertoireItemId,
                choirId);

        Long vocalPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class,
                repertoireItemId);

        jdbcTemplate.update(
                "INSERT INTO vocal_plan_divisions "
                        + "(vocal_plan_id, choir_id, vocal_section_id, division_number) "
                        + "VALUES (?, ?, ?, ?)",
                vocalPlanId,
                choirId,
                vocalSectionId,
                1);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plan_divisions "
                        + "WHERE vocal_plan_id = ? AND vocal_section_id = ? "
                        + "AND division_number = ?",
                Long.class,
                vocalPlanId,
                vocalSectionId,
                1);
    }

    private void insertAssignment(
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            Long divisionId) {
        jdbcTemplate.update(
                "INSERT INTO concert_vocal_assignments "
                        + "(concert_id, repertoire_item_id, singer_id, "
                        + "vocal_plan_division_id, choir_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                concertId,
                repertoireItemId,
                singerId,
                divisionId,
                choirId);
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

        Map<String, Object> position = jdbcTemplate.queryForMap(
                "SELECT singer_id, position_label, singer_name_snapshot, "
                        + "x_coordinate, y_coordinate "
                        + "FROM formation_positions "
                        + "WHERE formation_version_id = ?",
                fixture.versionId());

        assertThat(((Number) position.get("singer_id")).longValue())
                .isEqualTo(fixture.singerId());
        assertThat(position.get("position_label"))
                .isEqualTo("Posición anterior");
        assertThat(position.get("singer_name_snapshot"))
                .isEqualTo("Snapshot anterior");
        assertThat(new BigDecimal(position.get("x_coordinate").toString()))
                .isEqualByComparingTo("10.000");
        assertThat(new BigDecimal(position.get("y_coordinate").toString()))
                .isEqualByComparingTo("20.000");
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
            Long versionId,
            Long vocalSectionId,
            Long divisionId) {}
}
