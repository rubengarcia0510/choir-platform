package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort.CreatedFormation;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FormationCatalogInitialDraftIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String FAILURE_CONSTRAINT =
            "choir100_force_initial_position_failure";

    @Autowired
    private FormationCatalogPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @AfterEach
    void cleanUp() {
        try {
            jdbc.execute(
                    "ALTER TABLE formation_positions DROP CONSTRAINT IF EXISTS "
                            + FAILURE_CONSTRAINT);
        } finally {
            jdbc.update(
                    """
                    DELETE FROM formation_positions
                    WHERE formation_version_id IN (
                        SELECT fv.id
                        FROM formation_versions fv
                        JOIN formations f ON f.id = fv.formation_id
                        WHERE f.choir_id = ?
                    )
                    """,
                    fixture.choirId());

            jdbc.update(
                    """
                    DELETE FROM formation_versions
                    WHERE formation_id IN (
                        SELECT id FROM formations WHERE choir_id = ?
                    )
                    """,
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM formations WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM concert_vocal_adjustments WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    """
                    DELETE FROM concert_repertoire
                    WHERE concert_id IN (
                        SELECT id FROM concerts WHERE choir_id = ?
                    )
                    """,
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM concerts WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM vocal_plans WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM singers WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM vocal_sections WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM repertoire_items WHERE choir_id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM choirs WHERE id = ?",
                    fixture.choirId());

            jdbc.update(
                    "DELETE FROM directors WHERE id = ?",
                    fixture.directorId());
        }
    }

    @Test
    void createsInitialDraftAndCopiesActiveAssignmentSnapshots() {
        CreatedFormation created = adapter.createWithInitialDraft(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationName(),
                        "Formación creada por prueba de integración")
                .block(TIMEOUT);

        assertThat(created).isNotNull();
        assertThat(created.formation().getId()).isNotNull();
        assertThat(created.formation().getChoirId())
                .isEqualTo(fixture.choirId());
        assertThat(created.formation().getConcertId())
                .isEqualTo(fixture.concertId());
        assertThat(created.formation().getRepertoireItemId())
                .isEqualTo(fixture.repertoireItemId());

        assertThat(created.draftVersion().getId()).isNotNull();
        assertThat(created.draftVersion().getFormationId())
                .isEqualTo(created.formation().getId());
        assertThat(created.draftVersion().getVersionNumber()).isEqualTo(1);
        assertThat(created.draftVersion().getStatus()).isEqualTo("DRAFT");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formation_positions "
                        + "WHERE formation_version_id = ?",
                Integer.class,
                created.draftVersion().getId()))
                .isEqualTo(1);

        Map<String, Object> snapshot = jdbc.queryForMap(
                """
                SELECT singer_id, singer_name_snapshot, voice_part_snapshot,
                       voice_section_id_snapshot, voice_section_name_snapshot,
                       division_number_snapshot, x_coordinate, y_coordinate
                FROM formation_positions
                WHERE formation_version_id = ?
                """,
                created.draftVersion().getId());

        assertThat(((Number) snapshot.get("singer_id")).longValue())
                .isEqualTo(fixture.singerId());
        assertThat(snapshot.get("singer_name_snapshot"))
                .isEqualTo(fixture.singerName());
        assertThat(snapshot.get("voice_part_snapshot")).isEqualTo("SOPRANO");
        assertThat(((Number) snapshot.get("voice_section_id_snapshot")).longValue())
                .isEqualTo(fixture.sectionId());
        assertThat(snapshot.get("voice_section_name_snapshot"))
                .isEqualTo(fixture.sectionName());
        assertThat(((Number) snapshot.get("division_number_snapshot")).intValue())
                .isEqualTo(1);
        assertThat(snapshot.get("x_coordinate").toString()).isEqualTo("10.000");
        assertThat(snapshot.get("y_coordinate").toString()).isEqualTo("50.000");
    }

    @Test
    void persistenceFailureRollsBackFormationAndInitialDraft() {
        int formationsBefore = countFormations();
        int versionsBefore = countVersions();

        jdbc.execute(
                "ALTER TABLE formation_positions ADD CONSTRAINT "
                        + FAILURE_CONSTRAINT
                        + " CHECK (singer_id <> " + fixture.singerId() + ")");

        try {
            assertThatThrownBy(() -> adapter.createWithInitialDraft(
                            fixture.choirId(),
                            fixture.concertId(),
                            fixture.repertoireItemId(),
                            fixture.formationName(),
                            "Esta creación debe revertirse")
                    .block(TIMEOUT))
                    .isNotNull();

            assertThat(countFormations()).isEqualTo(formationsBefore);
            assertThat(countVersions()).isEqualTo(versionsBefore);

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM formations "
                            + "WHERE choir_id = ? AND concert_id = ? "
                            + "AND repertoire_item_id = ? AND name = ?",
                    Integer.class,
                    fixture.choirId(),
                    fixture.concertId(),
                    fixture.repertoireItemId(),
                    fixture.formationName()))
                    .isZero();
        } finally {
            jdbc.execute(
                    "ALTER TABLE formation_positions DROP CONSTRAINT IF EXISTS "
                            + FAILURE_CONSTRAINT);
        }
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        String email = "choir100-" + suffix + "@example.test";
        String choirName = "Coro CHOIR-100 " + suffix;
        String singerName = "Cantante CHOIR-100 " + suffix;
        String concertTitle = "Concierto CHOIR-100 " + suffix;
        String repertoireTitle = "Obra CHOIR-100 " + suffix;
        String sectionName = "Sección CHOIR-100 " + suffix;
        String formationName = "Formación CHOIR-100 " + suffix;

        jdbc.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director CHOIR-100 " + suffix,
                email);

        Long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                email);

        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                choirName);

        Long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                choirName);

        jdbc.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                concertTitle);

        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                concertTitle);

        jdbc.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                repertoireTitle);

        Long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                repertoireTitle);

        jdbc.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, ?)
                """,
                concertId,
                repertoireItemId,
                0);

        jdbc.update(
                "INSERT INTO vocal_sections (choir_id, name, sort_order) "
                        + "VALUES (?, ?, ?)",
                choirId,
                sectionName,
                0);

        Long sectionId = jdbc.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                choirId,
                sectionName);

        jdbc.update(
                "INSERT INTO singers "
                        + "(choir_id, display_name, voice_part, active, voice_section_id) "
                        + "VALUES (?, ?, ?, TRUE, ?)",
                choirId,
                singerName,
                "SOPRANO",
                sectionId);

        Long singerId = jdbc.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                singerName);

        jdbc.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) "
                        + "VALUES (?, ?)",
                repertoireItemId,
                choirId);

        Long vocalPlanId = jdbc.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class,
                repertoireItemId);

        jdbc.update(
                """
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, ?)
                """,
                vocalPlanId,
                choirId,
                sectionId,
                1);

        Long divisionId = jdbc.queryForObject(
                "SELECT id FROM vocal_plan_divisions "
                        + "WHERE vocal_plan_id = ? AND vocal_section_id = ? "
                        + "AND division_number = ?",
                Long.class,
                vocalPlanId,
                sectionId,
                1);

        jdbc.update(
                """
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id,
                     vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                concertId,
                repertoireItemId,
                singerId,
                divisionId,
                choirId);

        return new Fixture(
                directorId,
                choirId,
                concertId,
                repertoireItemId,
                singerId,
                singerName,
                sectionId,
                sectionName,
                formationName);
    }

    private int countFormations() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM formations WHERE choir_id = ?",
                Integer.class,
                fixture.choirId());
    }

    private int countVersions() {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM formation_versions fv
                JOIN formations f ON f.id = fv.formation_id
                WHERE f.choir_id = ?
                """,
                Integer.class,
                fixture.choirId());
    }

    private record Fixture(
            Long directorId,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            String singerName,
            Long sectionId,
            String sectionName,
            String formationName) {
    }
}
