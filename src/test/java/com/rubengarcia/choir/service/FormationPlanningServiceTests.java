package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.usecase.FormationPlanningService;
import com.rubengarcia.choir.formation.application.usecase.FormationPlanningServiceImpl;
import com.rubengarcia.choir.transition.adapter.out.persistence.R2dbcTransitionPlanningPositionRepository;
import com.rubengarcia.choir.transition.application.usecase.TransitionPlanningService;
import com.rubengarcia.choir.transition.application.usecase.TransitionPlanningServiceImpl;
import com.rubengarcia.choir.transition.domain.TransitionPlanningPosition;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class FormationPlanningServiceTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DatabaseClient database;

    private FormationPlanningService service;

    private TransitionPlanningService transitionPlanningService;

    @org.junit.jupiter.api.BeforeEach
    void setUpPlanningService() {
        service = new FormationPlanningServiceImpl(database);
        transitionPlanningService = new TransitionPlanningServiceImpl(
                new R2dbcTransitionPlanningPositionRepository(database));
    }

    @Test
    void readsHistoricalVoiceDivisionFromFormationPosition() {
        Fixture f = fixture("DRAFT");

        var positions = service.positionsForFormationVersion(f.versionId())
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).singerName()).isEqualTo("Nombre histórico");
        assertThat(positions.get(0).voicePart()).isEqualTo("SOPRANO");
        assertThat(positions.get(0).voiceSectionName()).isEqualTo("Sopranos");
        assertThat(positions.get(0).divisionNumber()).isEqualTo(2);
    }

    @Test
    void updatesPositionInDraftWithoutChangingHistoricalVocalData() {
        Fixture f = fixture("DRAFT");

        service.updateDraftPosition(
                f.positionId(),
                "Nueva ubicación",
                new BigDecimal("3.000"),
                new BigDecimal("4.000"))
                .block(Duration.ofSeconds(10));

        assertThat(jdbc.queryForObject(
                "SELECT position_label FROM formation_positions WHERE id = ?",
                String.class, f.positionId()))
                .isEqualTo("Nueva ubicación");

        assertThat(jdbc.queryForObject(
                "SELECT division_number_snapshot FROM formation_positions WHERE id = ?",
                Integer.class, f.positionId()))
                .isEqualTo(2);

        assertThat(jdbc.queryForObject(
                "SELECT voice_section_name_snapshot FROM formation_positions WHERE id = ?",
                String.class, f.positionId()))
                .isEqualTo("Sopranos");
    }

    @Test
    void rejectsUpdatesToPublishedFormationPosition() {
        Fixture f = fixture("PUBLISHED");

        assertThatThrownBy(() -> service.updateDraftPosition(
                f.positionId(),
                "Cambio no permitido",
                new BigDecimal("8.000"),
                new BigDecimal("9.000"))
                .block(Duration.ofSeconds(10)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No se puede modificar una formación publicada");

        assertThat(jdbc.queryForObject(
                "SELECT position_label FROM formation_positions WHERE id = ?",
                String.class, f.positionId()))
                .isEqualTo("Posición original");
    }

    @Test
    void returnsVocalAssignmentsOnlyForPublishedConcerts() {
        Fixture f = fixture("DRAFT");

        long concertId = insertConcert(f.choirId(), "PUBLISHED");
        long repertoireId = insertRepertoire(f.choirId());
        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireId);

        long planId = insertVocalPlan(repertoireId, f.choirId());
        long divisionId = insertDivision(
                planId, f.choirId(), f.sectionId(), 2);

        jdbc.update("""
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id,
                     vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """, concertId, repertoireId, f.singerId(),
                divisionId, f.choirId());

        jdbc.update("""
                INSERT INTO concert_vocal_assignment_snapshots
                    (concert_id, repertoire_item_id, source_singer_id,
                     singer_name_snapshot, voice_part_snapshot,
                     voice_section_id_snapshot, voice_section_name_snapshot,
                     division_number_snapshot)
                VALUES (?, ?, ?, 'Nombre histórico', 'SOPRANO',
                        ?, 'Sopranos', 2)
                """, concertId, repertoireId, f.singerId(), f.sectionId());

        var assignments = service.vocalAssignmentsForPlanning(
                concertId, repertoireId)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).singerName()).isEqualTo("Nombre histórico");
        assertThat(assignments.get(0).divisionNumber()).isEqualTo(2);

        long plannedConcertId = insertConcert(f.choirId(), "PLANNED");
        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, plannedConcertId, repertoireId);

        assertThat(service.vocalAssignmentsForPlanning(
                plannedConcertId, repertoireId)
                .collectList()
                .block(Duration.ofSeconds(10)))
                .isEmpty();
    }

    @Test
    void renamingSectionAndReassigningSingerPreservesFormationSnapshot() {
        Fixture f = fixture("DRAFT");

        jdbc.update("INSERT INTO vocal_sections (choir_id, name) VALUES (?, ?)",
                f.choirId(), "Contraltos");
        long newSectionId = jdbc.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class, f.choirId(), "Contraltos");

        jdbc.update("UPDATE vocal_sections SET name = ? WHERE id = ?",
                "Voces antiguas", f.sectionId());
        jdbc.update("UPDATE singers SET voice_section_id = ? WHERE id = ?",
                newSectionId, f.singerId());

        var positions = service.positionsForFormationVersion(f.versionId())
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).voiceSectionId()).isEqualTo(f.sectionId());
        assertThat(positions.get(0).voiceSectionName()).isEqualTo("Sopranos");
        assertThat(positions.get(0).divisionNumber()).isEqualTo(2);
    }

    @Test
    void exposesHistoricalVocalDivisionsForBothSidesOfTransition() {
        Fixture origin = fixture("PUBLISHED");

        jdbc.update("INSERT INTO formations (choir_id, name) VALUES (?, ?)",
                origin.choirId(), "Formación destino " + UUID.randomUUID());
        long destinationFormationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name LIKE ? ORDER BY id DESC",
                Long.class, origin.choirId(), "Formación destino %");

        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED')
                """, destinationFormationId);
        long destinationVersionId = jdbc.queryForObject(
                "SELECT id FROM formation_versions WHERE formation_id = ? AND version_number = 1",
                Long.class, destinationFormationId);

        jdbc.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot,
                     position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, 'Nombre destino', 'ALTO', ?, 'Altos', 3,
                        'Destino', 5.000, 6.000)
                """, destinationVersionId, origin.singerId(), origin.sectionId());

        jdbc.update("INSERT INTO transitions (choir_id, name) VALUES (?, ?)",
                origin.choirId(), "Transición de prueba " + UUID.randomUUID());
        long transitionId = jdbc.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? ORDER BY id DESC",
                Long.class, origin.choirId());

        jdbc.update("""
                INSERT INTO transition_versions
                    (transition_id, version_number, from_formation_version_id,
                     to_formation_version_id, status)
                VALUES (?, 1, ?, ?, 'PUBLISHED')
                """, transitionId, origin.versionId(), destinationVersionId);
        long transitionVersionId = jdbc.queryForObject(
                "SELECT id FROM transition_versions WHERE transition_id = ? AND version_number = 1",
                Long.class, transitionId);

        var positions = transitionPlanningService
                .positionsForTransitionVersion(transitionVersionId)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(positions).hasSize(2);
        assertThat(positions)
                .extracting(TransitionPlanningPosition::stage)
                .containsExactly("FROM", "TO");
        assertThat(positions)
                .extracting(TransitionPlanningPosition::divisionNumber)
                .containsExactly(2, 3);
        assertThat(positions)
                .extracting(TransitionPlanningPosition::voiceSectionName)
                .containsExactly("Sopranos", "Altos");
    }

    private Fixture fixture(String versionStatus) {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director de prueba', ?)
                """, email);
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);

        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro " + suffix);
        long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro " + suffix);

        jdbc.update("""
                INSERT INTO vocal_sections (choir_id, name)
                VALUES (?, 'Sopranos')
                """, choirId);
        long sectionId = jdbc.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ?",
                Long.class, choirId);

        jdbc.update("""
                INSERT INTO singers
                    (choir_id, display_name, voice_part, voice_section_id)
                VALUES (?, 'Nombre histórico', 'SOPRANO', ?)
                """, choirId, sectionId);
        long singerId = jdbc.queryForObject("""
                SELECT id FROM singers
                WHERE choir_id = ? AND display_name = 'Nombre histórico'
                """, Long.class, choirId);

        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación " + suffix);
        long formationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Formación " + suffix);

        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status)
                VALUES (?, 1, ?)
                """, formationId, versionStatus);
        long versionId = jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);

        jdbc.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, voice_section_id_snapshot,
                     voice_section_name_snapshot, division_number_snapshot,
                     position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, 'Nombre histórico', 'SOPRANO', ?,
                        'Sopranos', 2, 'Posición original', 1.000, 2.000)
                """, versionId, singerId, sectionId);
        long positionId = jdbc.queryForObject("""
                SELECT id FROM formation_positions
                WHERE formation_version_id = ?
                """, Long.class, versionId);

        return new Fixture(choirId, sectionId, singerId, versionId, positionId);
    }

    private long insertConcert(long choirId, String status) {
        String title = "Concierto " + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, ?)
                """, choirId, title, status);
        return jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
    }

    private long insertRepertoire(long choirId) {
        String title = "Obra " + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, title);
        return jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
    }

    private long insertVocalPlan(long repertoireId, long choirId) {
        jdbc.update("""
                INSERT INTO vocal_plans (repertoire_item_id, choir_id)
                VALUES (?, ?)
                """, repertoireId, choirId);
        return jdbc.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class, repertoireId);
    }

    private long insertDivision(
            long planId, long choirId, long sectionId, int number) {
        jdbc.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """, planId, choirId, sectionId, number);
        return jdbc.queryForObject("""
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                  AND division_number = ?
                """, Long.class, planId, sectionId, number);
    }

    private record Fixture(
            long choirId,
            long sectionId,
            long singerId,
            long versionId,
            long positionId) {
    }
}
