package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.application.port.in.FormationMobilityEvaluationUseCase;
import com.rubengarcia.choir.formation.domain.MobilityBound;
import com.rubengarcia.choir.formation.domain.MobilityEvaluation;
import com.rubengarcia.choir.formation.domain.MobilityEvaluationStatus;
import com.rubengarcia.choir.formation.domain.MobilityNotEvaluableReason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationMobilityEvaluationIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String PASSWORD = "Evaluation-Test-2026!";

    private final FormationMobilityEvaluationUseCase evaluation;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final List<Long> directorIds = new ArrayList<>();
    private final List<Long> choirIds = new ArrayList<>();

    FormationMobilityEvaluationIntegrationTests(
            FormationMobilityEvaluationUseCase evaluation,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.evaluation = evaluation;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void cleanUp() {
        for (Long choirId : choirIds) {
            jdbcTemplate.update("DELETE FROM formation_mobility_constraints WHERE choir_id = ?", choirId);
            jdbcTemplate.update("""
                    DELETE FROM formation_positions WHERE formation_version_id IN (
                        SELECT v.id FROM formation_versions v
                        JOIN formations f ON f.id = v.formation_id WHERE f.choir_id = ?)
                    """, choirId);
            jdbcTemplate.update("""
                    DELETE FROM formation_versions WHERE formation_id IN (
                        SELECT id FROM formations WHERE choir_id = ?)
                    """, choirId);
            jdbcTemplate.update("DELETE FROM formations WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM concert_vocal_assignments WHERE choir_id = ?", choirId);
            jdbcTemplate.update("""
                    DELETE FROM concert_repertoire WHERE concert_id IN (
                        SELECT id FROM concerts WHERE choir_id = ?)
                    """, choirId);
            jdbcTemplate.update("DELETE FROM vocal_plan_divisions WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM vocal_plans WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM concerts WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM singers WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM vocal_sections WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM repertoire_items WHERE choir_id = ?", choirId);
            jdbcTemplate.update("DELETE FROM choirs WHERE id = ?", choirId);
        }
        for (Long directorId : directorIds) {
            jdbcTemplate.update("DELETE FROM directors WHERE id = ?", directorId);
        }
        choirIds.clear();
        directorIds.clear();
    }

    @Test
    void reportsOnlyPositionsThatBreakTheirActiveConstraint() {
        Context ctx = newContext();
        long inside = insertSinger(ctx.choirId(), "dentro");
        long outside = insertSinger(ctx.choirId(), "fuera");
        long inactive = insertSinger(ctx.choirId(), "inactiva");
        insertConstraint(ctx.formationId(), ctx.choirId(), inside, true);
        insertConstraint(ctx.formationId(), ctx.choirId(), outside, true);
        insertConstraint(ctx.formationId(), ctx.choirId(), inactive, false);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, inside, "4.000", "5.000");
        insertPosition(version, outside, "9.000", "5.000");
        insertPosition(version, inactive, "99.000", "99.000");

        MobilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.singerId()).isEqualTo(outside);
            assertThat(finding.violatedBounds()).containsExactly(MobilityBound.X_ABOVE_MAX);
        });
        assertThat(result.notEvaluable()).isEmpty();
    }

    @Test
    void activeConstraintWithoutDraftPositionIsNotEvaluable() {
        Context ctx = newContext();
        long singer = insertSinger(ctx.choirId(), "sin posición");
        insertConstraint(ctx.formationId(), ctx.choirId(), singer, true);
        insertDraft(ctx.formationId());

        MobilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(MobilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.notEvaluable()).singleElement()
                .satisfies(entry -> assertThat(entry.reason())
                        .isEqualTo(MobilityNotEvaluableReason.MISSING_DRAFT_POSITION));
    }

    @Test
    void constraintsOfAnotherFormationAreNotConsidered() {
        Context ctx = newContext();
        long singer = insertSinger(ctx.choirId(), "compartido");
        // Restricción propia inactiva: no debe producir hallazgo.
        insertConstraint(ctx.formationId(), ctx.choirId(), singer, false);
        // Restricción activa de otra formación que la posición de esta sí rompería:
        // si se filtrara, aparecería un hallazgo.
        long otherFormation = insertFormation(ctx.choirId(), ctx.concertId(),
                ctx.repertoireItemId(), "otra formación");
        insertConstraint(otherFormation, ctx.choirId(), singer, true);
        long otherVersion = insertDraft(otherFormation);
        insertPosition(otherVersion, singer, "9.000", "5.000");
        long version = insertDraft(ctx.formationId());
        insertPosition(version, singer, "9.000", "5.000");

        MobilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(MobilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.notEvaluable()).isEmpty();
    }

    @Test
    void evaluationNeverModifiesPositionsOrConstraints() {
        Context ctx = newContext();
        long singer = insertSinger(ctx.choirId(), "solo lectura");
        insertConstraint(ctx.formationId(), ctx.choirId(), singer, true);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, singer, "9.000", "5.000");

        List<Map<String, Object>> positionsBefore = rows(
                "SELECT * FROM formation_positions WHERE formation_version_id = ?", version);
        List<Map<String, Object>> constraintsBefore = rows(
                "SELECT * FROM formation_mobility_constraints WHERE formation_id = ?",
                ctx.formationId());

        evaluate(ctx);

        assertThat(rows(
                "SELECT * FROM formation_positions WHERE formation_version_id = ?", version))
                .isEqualTo(positionsBefore);
        assertThat(rows(
                "SELECT * FROM formation_mobility_constraints WHERE formation_id = ?",
                ctx.formationId()))
                .isEqualTo(constraintsBefore);
    }

    // ------------------------------------------------------------------

    private MobilityEvaluation evaluate(Context ctx) {
        return evaluation.evaluate(
                        ctx.email(), ctx.choirId(), ctx.concertId(),
                        ctx.repertoireItemId(), ctx.formationId())
                .block(TIMEOUT);
    }

    private List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }

    private Context newContext() {
        String unique = UUID.randomUUID().toString();
        String email = "evaluation-" + unique + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO directors (display_name, email, password_hash, email_verified)
                VALUES (?, ?, ?, TRUE)
                """, "Director evaluación " + unique, email,
                passwordEncoder.encode(PASSWORD));
        Long directorId = jdbcTemplate.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        directorIds.add(directorId);

        jdbcTemplate.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Coro evaluación " + unique);
        Long choirId = jdbcTemplate.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro evaluación " + unique);
        choirIds.add(choirId);

        jdbcTemplate.update("INSERT INTO concerts (choir_id, title) VALUES (?, ?)",
                choirId, "Concierto evaluación " + unique);
        Long concertId = jdbcTemplate.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Concierto evaluación " + unique);

        jdbcTemplate.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, "Obra evaluación " + unique);
        Long repertoireItemId = jdbcTemplate.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Obra evaluación " + unique);

        jdbcTemplate.update("""
                INSERT INTO concert_repertoire (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO vocal_sections (choir_id, name, sort_order)
                VALUES (?, ?, 0)
                """, choirId, "Sección evaluación " + unique);
        Long sectionId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_sections WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Sección evaluación " + unique);

        jdbcTemplate.update(
                "INSERT INTO vocal_plans (repertoire_item_id, choir_id) VALUES (?, ?)",
                repertoireItemId, choirId);
        Long planId = jdbcTemplate.queryForObject(
                "SELECT id FROM vocal_plans WHERE repertoire_item_id = ?",
                Long.class, repertoireItemId);

        jdbcTemplate.update("""
                INSERT INTO vocal_plan_divisions
                    (vocal_plan_id, choir_id, vocal_section_id, division_number)
                VALUES (?, ?, ?, 1)
                """, planId, choirId, sectionId);

        long formationId = insertFormation(choirId, concertId, repertoireItemId,
                "Formación evaluación " + unique);

        return new Context(email, choirId, concertId, repertoireItemId, formationId);
    }

    private long insertFormation(long choirId, long concertId, long repertoireItemId, String name) {
        jdbcTemplate.update("""
                INSERT INTO formations
                    (choir_id, concert_id, repertoire_item_id, name, description)
                VALUES (?, ?, ?, ?, 'Fixture de evaluación')
                """, choirId, concertId, repertoireItemId, name);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM formations
                WHERE choir_id = ? AND concert_id = ? AND repertoire_item_id = ? AND name = ?
                """, Long.class, choirId, concertId, repertoireItemId, name);
    }

    private long insertDraft(long formationId) {
        jdbcTemplate.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, ?, 'DRAFT')
                """, formationId, nextVersionNumber(formationId));
        return jdbcTemplate.queryForObject("""
                SELECT MAX(id) FROM formation_versions
                WHERE formation_id = ? AND status = 'DRAFT'
                """, Long.class, formationId);
    }

    private int nextVersionNumber(long formationId) {
        Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_number), 0) FROM formation_versions WHERE formation_id = ?",
                Integer.class, formationId);
        return max + 1;
    }

    private long insertSinger(long choirId, String name) {
        jdbcTemplate.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'ALTO', TRUE)
                """, choirId, name + " " + UUID.randomUUID());
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM singers WHERE choir_id = ?", Long.class, choirId);
    }

    private void insertPosition(long versionId, long singerId, String x, String y) {
        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, position_label, x_coordinate, y_coordinate)
                VALUES (?, ?, 'Cantante', 'ALTO', 'Posición', ?, ?)
                """, versionId, singerId, new java.math.BigDecimal(x), new java.math.BigDecimal(y));
    }

    private void insertConstraint(long formationId, long choirId, long singerId, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO formation_mobility_constraints
                    (formation_id, choir_id, singer_id, min_x, max_x, min_y, max_y, active)
                VALUES (?, ?, ?, 1.000, 8.000, 0.000, 10.000, ?)
                """, formationId, choirId, singerId, active);
    }

    private record Context(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
    }
}
