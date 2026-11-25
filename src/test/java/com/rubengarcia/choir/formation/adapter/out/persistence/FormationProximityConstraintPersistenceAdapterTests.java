package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.domain.FormationProximityConstraint;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FormationProximityConstraintPersistenceAdapterTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private FormationProximityConstraintPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = createFixture();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                """
                DELETE FROM formation_proximity_constraints
                WHERE formation_id = ?
                """,
                fixture.formationId());

        jdbcTemplate.update(
                """
                DELETE FROM formation_positions
                WHERE formation_version_id IN (
                    SELECT id FROM formation_versions WHERE formation_id = ?
                )
                """,
                fixture.formationId());

        jdbcTemplate.update(
                "DELETE FROM formation_versions WHERE formation_id = ?",
                fixture.formationId());

        jdbcTemplate.update(
                "DELETE FROM formations WHERE id = ?",
                fixture.formationId());

        jdbcTemplate.update(
                "DELETE FROM concert_vocal_assignments WHERE choir_id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                """
                DELETE FROM concert_repertoire
                WHERE concert_id = ?
                """,
                fixture.concertId());

        jdbcTemplate.update(
                "DELETE FROM vocal_plan_divisions WHERE choir_id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                "DELETE FROM vocal_plans WHERE choir_id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                "DELETE FROM concerts WHERE id = ?",
                fixture.concertId());

        jdbcTemplate.update(
                "DELETE FROM singers WHERE choir_id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                "DELETE FROM vocal_sections WHERE choir_id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                "DELETE FROM repertoire_items WHERE choir_id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                "DELETE FROM choirs WHERE id = ?",
                fixture.choirId());

        jdbcTemplate.update(
                "DELETE FROM directors WHERE id = ?",
                fixture.directorId());
    }

    @Test
    void createsListsUpdatesAndDeletesConstraint() {
        FormationProximityConstraint created = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        fixture.singerTwoId(),
                        new BigDecimal("4.500"))
                .block(TIMEOUT);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.formationId()).isEqualTo(fixture.formationId());
        assertThat(created.singerOneId()).isEqualTo(fixture.singerOneId());
        assertThat(created.singerTwoId()).isEqualTo(fixture.singerTwoId());
        assertThat(created.maxDistance()).isEqualByComparingTo("4.500");
        assertThat(created.active()).isTrue();
        assertThat(created.createdAt()).isNotNull();

        var listed = adapter.findForFormation(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId())
                .collectList()
                .block(TIMEOUT);

        assertThat(listed).containsExactly(created);

        FormationProximityConstraint updated = adapter.update(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        created.id(),
                        new BigDecimal("6.250"),
                        false)
                .block(TIMEOUT);

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.maxDistance()).isEqualByComparingTo("6.250");
        assertThat(updated.active()).isFalse();

        assertThat(adapter.delete(
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                fixture.formationId(),
                created.id()).block(TIMEOUT)).isTrue();

        assertThat(adapter.findForFormation(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId())
                .collectList()
                .block(TIMEOUT))
                .isEmpty();

        assertThat(adapter.delete(
                fixture.choirId(),
                fixture.concertId(),
                fixture.repertoireItemId(),
                fixture.formationId(),
                created.id()).block(TIMEOUT)).isFalse();
    }

    @Test
    void requiresBothSingersInTheSameDraftVersion() {
        jdbcTemplate.update(
                """
                DELETE FROM formation_positions
                WHERE formation_version_id = ? AND singer_id = ?
                """,
                fixture.versionId(),
                fixture.singerTwoId());

        Long secondVersionId = createDraftVersion(
                fixture.formationId(),
                2);

        insertPosition(
                secondVersionId,
                fixture.singerTwoId(),
                "Segundo cantante en otro borrador");

        assertThatThrownBy(() -> adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        fixture.singerTwoId(),
                        new BigDecimal("5.000"))
                .block(TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mismo borrador");

        assertThat(countConstraints()).isZero();

        insertPosition(
                fixture.versionId(),
                fixture.singerTwoId(),
                "Segundo cantante en el mismo borrador");

        FormationProximityConstraint created = adapter.create(
                        fixture.choirId(),
                        fixture.concertId(),
                        fixture.repertoireItemId(),
                        fixture.formationId(),
                        fixture.singerOneId(),
                        fixture.singerTwoId(),
                        new BigDecimal("5.000"))
                .block(TIMEOUT);

        assertThat(created).isNotNull();
        assertThat(countConstraints()).isEqualTo(1);
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "INSERT INTO directors (display_name, email) VALUES (?, ?)",
                "Director proximidad " + suffix,
                "proximity-" + suffix + "@example.test");

        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class,
                "proximity-" + suffix + "@example.test");

        jdbcTemplate.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId,
                "Coro proximidad " + suffix);

        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class,
                directorId,
                "Coro proximidad " + suffix);

        Long singerOneId = insertSinger(
                choirId, "Primera cantante " + suffix);
        Long singerTwoId = insertSinger(
                choirId, "Segunda cantante " + suffix);

        jdbcTemplate.update(
                "INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId,
                "Concierto proximidad " + suffix);

        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Concierto proximidad " + suffix);

        jdbcTemplate.update(
                "INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId,
                "Obra proximidad " + suffix);

        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class,
                choirId,
                "Obra proximidad " + suffix);

        jdbcTemplate.update(
                """
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """,
                concertId,
                repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO vocal_sections (choir_id, name, sort_order)
                VALUES (?, ?, 0)
                """,
                choirId,
                "Sección proximidad " + suffix);

        Long sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class,
                choirId,
                "Sección proximidad " + suffix);

        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) VALUES (?, ?)",
                repertoireItemId,
                choirId);

        Long vocalPlanId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class,
                repertoireItemId);

        jdbcTemplate.update(
                """
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, 1)
                """,
                vocalPlanId,
                choirId,
                sectionId);

        Long divisionId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM vocal_plan_divisions
                WHERE vocal_plan_id = ? AND vocal_section_id = ?
                """,
                Long.class,
                vocalPlanId,
                sectionId);

        insertAssignment(
                choirId, concertId, repertoireItemId, singerOneId, divisionId);
        insertAssignment(
                choirId, concertId, repertoireItemId, singerTwoId, divisionId);

        jdbcTemplate.update(
                """
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, ?)
                """,
                choirId,
                concertId,
                repertoireItemId,
                "Formación proximidad " + suffix,
                "Fixture de restricciones de proximidad");

        Long formationId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ?
                  AND repertoire_item_id = ? AND name = ?
                """,
                Long.class,
                choirId,
                concertId,
                repertoireItemId,
                "Formación proximidad " + suffix);

        Long versionId = createDraftVersion(formationId, 1);

        insertPosition(versionId, singerOneId, "Primera cantante");
        insertPosition(versionId, singerTwoId, "Segunda cantante");

        return new Fixture(
                directorId,
                choirId,
                concertId,
                repertoireItemId,
                formationId,
                versionId,
                singerOneId,
                singerTwoId);
    }

    private Long insertSinger(Long choirId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'ALTO', TRUE)
                """,
                choirId,
                name);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class,
                choirId,
                name);
    }

    private void insertAssignment(
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long singerId,
            Long divisionId) {
        jdbcTemplate.update(
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
    }

    private Long createDraftVersion(Long formationId, int versionNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, ?, 'DRAFT')
                """,
                formationId,
                versionNumber);

        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = ?
                """,
                Long.class,
                formationId,
                versionNumber);
    }

    private void insertPosition(
            Long versionId,
            Long singerId,
            String label) {
        String singerName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM singers WHERE id = ?",
                String.class,
                singerId);

        jdbcTemplate.update(
                """
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, ?, 'ALTO', ?, 10.000, 20.000)
                """,
                versionId,
                singerId,
                singerName,
                label);
    }

    private int countConstraints() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM formation_proximity_constraints
                WHERE formation_id = ?
                """,
                Integer.class,
                fixture.formationId());
    }

    private record Fixture(
            Long directorId,
            Long choirId,
            Long concertId,
            Long repertoireItemId,
            Long formationId,
            Long versionId,
            Long singerOneId,
            Long singerTwoId) {
    }
}
