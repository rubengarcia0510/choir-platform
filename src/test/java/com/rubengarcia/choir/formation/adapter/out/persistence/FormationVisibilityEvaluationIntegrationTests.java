package com.rubengarcia.choir.formation.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.formation.application.port.in.FormationVisibilityEvaluationUseCase;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluation;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluationStatus;
import com.rubengarcia.choir.formation.domain.VisibilityNotEvaluableReason;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FormationVisibilityEvaluationIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String PASSWORD = "Visibility-Evaluation-Test-2026!";

    private final FormationVisibilityEvaluationUseCase evaluation;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final List<Long> directorIds = new ArrayList<>();
    private final List<Long> choirIds = new ArrayList<>();

    FormationVisibilityEvaluationIntegrationTests(
            FormationVisibilityEvaluationUseCase evaluation,
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
            jdbcTemplate.update("DELETE FROM formation_visibility_constraints WHERE choir_id = ?", choirId);
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
    void reportsOnlyObstructionsThatBreakTheVisibilityRule() {
        Context ctx = newContext();
        long protectedSinger = insertSinger(ctx.choirId(), "protegido");
        long obstructing = insertSinger(ctx.choirId(), "obstructor");
        long outOfReach = insertSinger(ctx.choirId(), "fuera de alcance");
        long behind = insertSinger(ctx.choirId(), "detrás");
        long shorter = insertSinger(ctx.choirId(), "más bajo");
        for (long singer : List.of(protectedSinger, obstructing, outOfReach, behind, shorter)) {
            insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), singer);
        }
        insertVisibilityConstraint(ctx.formationId(), ctx.choirId(), protectedSinger, true);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, protectedSinger, "10.000", "20.000", 170);
        insertPosition(version, obstructing, "11.000", "30.000", 180);
        insertPosition(version, outOfReach, "30.000", "30.000", 180);
        insertPosition(version, behind, "11.000", "10.000", 180);
        insertPosition(version, shorter, "11.000", "30.000", 160);

        VisibilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.protectedSingerId()).isEqualTo(protectedSinger);
            assertThat(finding.obstructingSingerId()).isEqualTo(obstructing);
            assertThat(finding.protectedHeightCm()).isEqualTo(170);
            assertThat(finding.obstructingHeightCm()).isEqualTo(180);
        });
        assertThat(result.notEvaluable()).isEmpty();
    }

    @Test
    void protectedSingerWithoutDraftPositionIsNotEvaluable() {
        Context ctx = newContext();
        long protectedSinger = insertSinger(ctx.choirId(), "sin posición");
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), protectedSinger);
        insertVisibilityConstraint(ctx.formationId(), ctx.choirId(), protectedSinger, true);
        insertDraft(ctx.formationId());

        VisibilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.notEvaluable()).singleElement()
                .satisfies(entry -> assertThat(entry.reason())
                        .isEqualTo(VisibilityNotEvaluableReason.MISSING_PROTECTED_DRAFT_POSITION));
    }

    @Test
    void singerOutsideTheAssignedRosterCannotObstruct() {
        Context ctx = newContext();
        long protectedSinger = insertSinger(ctx.choirId(), "protegido");
        long outsider = insertSinger(ctx.choirId(), "sin asignación");
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), protectedSinger);
        insertVisibilityConstraint(ctx.formationId(), ctx.choirId(), protectedSinger, true);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, protectedSinger, "10.000", "20.000", 170);
        insertPosition(version, outsider, "11.000", "30.000", 180);

        VisibilityEvaluation result = evaluate(ctx);

        assertThat(result.findings()).isEmpty();
        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.NOT_EVALUABLE);
        assertThat(result.notEvaluable()).singleElement().satisfies(entry -> {
            assertThat(entry.obstructingSingerId()).isEqualTo(outsider);
            assertThat(entry.reason())
                    .isEqualTo(VisibilityNotEvaluableReason.POSITION_NOT_IN_ASSIGNED_ROSTER);
        });
    }

    @Test
    void constraintsOfAnotherFormationAreNotConsidered() {
        Context ctx = newContext();
        long otherFormation = insertFormation(ctx.choirId(), ctx.concertId(),
                ctx.repertoireItemId(), "Formación visibilidad otra " + UUID.randomUUID());
        long protectedSinger = insertSinger(ctx.choirId(), "protegido");
        long obstructing = insertSinger(ctx.choirId(), "obstructor");
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), protectedSinger);
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), obstructing);
        insertVisibilityConstraint(otherFormation, ctx.choirId(), protectedSinger, true);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, protectedSinger, "10.000", "20.000", 170);
        insertPosition(version, obstructing, "11.000", "30.000", 180);

        VisibilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.notEvaluable()).isEmpty();
    }

    @Test
    void inactiveVisibilityConstraintIsIgnored() {
        Context ctx = newContext();
        long protectedSinger = insertSinger(ctx.choirId(), "protegido");
        long obstructing = insertSinger(ctx.choirId(), "obstructor");
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), protectedSinger);
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), obstructing);
        insertVisibilityConstraint(ctx.formationId(), ctx.choirId(), protectedSinger, false);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, protectedSinger, "10.000", "20.000", 170);
        insertPosition(version, obstructing, "11.000", "30.000", 180);

        VisibilityEvaluation result = evaluate(ctx);

        assertThat(result.status()).isEqualTo(VisibilityEvaluationStatus.EVALUABLE);
        assertThat(result.findings()).isEmpty();
        assertThat(result.notEvaluable()).isEmpty();
    }

    @Test
    void directorOfAnotherChoirCannotEvaluateVisibility() {
        Context owner = newContext();
        Context foreign = newContext();
        long protectedSinger = insertSinger(owner.choirId(), "protegido");
        insertAssignment(owner.choirId(), owner.concertId(), owner.repertoireItemId(), protectedSinger);
        insertVisibilityConstraint(owner.formationId(), owner.choirId(), protectedSinger, true);
        insertDraft(owner.formationId());

        assertThatThrownBy(() -> evaluation.evaluate(
                        foreign.email(),
                        owner.choirId(),
                        owner.concertId(),
                        owner.repertoireItemId(),
                        owner.formationId())
                .block(TIMEOUT))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void evaluationNeverModifiesPositionsOrConstraints() {
        Context ctx = newContext();
        long protectedSinger = insertSinger(ctx.choirId(), "protegido");
        long obstructing = insertSinger(ctx.choirId(), "obstructor");
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), protectedSinger);
        insertAssignment(ctx.choirId(), ctx.concertId(), ctx.repertoireItemId(), obstructing);
        insertVisibilityConstraint(ctx.formationId(), ctx.choirId(), protectedSinger, true);
        long version = insertDraft(ctx.formationId());
        insertPosition(version, protectedSinger, "10.000", "20.000", 170);
        insertPosition(version, obstructing, "11.000", "30.000", 180);
        List<Map<String, Object>> positionsBefore = rows(
                "SELECT * FROM formation_positions WHERE formation_version_id = ? ORDER BY id", version);
        List<Map<String, Object>> constraintsBefore = rows(
                "SELECT * FROM formation_visibility_constraints WHERE formation_id = ? ORDER BY id",
                ctx.formationId());

        evaluate(ctx);

        assertThat(rows(
                "SELECT * FROM formation_positions WHERE formation_version_id = ? ORDER BY id", version))
                .isEqualTo(positionsBefore);
        assertThat(rows(
                "SELECT * FROM formation_visibility_constraints WHERE formation_id = ? ORDER BY id",
                ctx.formationId()))
                .isEqualTo(constraintsBefore);
    }

    private VisibilityEvaluation evaluate(Context ctx) {
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

    private record Context(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
    }

    private void insertAssignment(long choirId, long concertId, long repertoireItemId, long singerId) {
        Long divisionId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM vocal_plan_divisions WHERE choir_id = ?", Long.class, choirId);
        jdbcTemplate.update("""
                INSERT INTO concert_vocal_assignments
                    (concert_id, repertoire_item_id, singer_id, vocal_plan_division_id, choir_id)
                VALUES (?, ?, ?, ?, ?)
                """, concertId, repertoireItemId, singerId, divisionId, choirId);
    }

    private void insertVisibilityConstraint(long formationId, long choirId, long protectedSingerId,
                                            boolean active) {
        jdbcTemplate.update("""
                INSERT INTO formation_visibility_constraints
                    (formation_id, choir_id, protected_singer_id, active)
                VALUES (?, ?, ?, ?)
                """, formationId, choirId, protectedSingerId, active);
    }

    private void insertPosition(long versionId, long singerId, String x, String y, int heightCm) {
        jdbcTemplate.update("""
                INSERT INTO formation_positions
                    (formation_version_id, singer_id, singer_name_snapshot,
                     voice_part_snapshot, position_label, x_coordinate, y_coordinate,
                     height_cm_snapshot)
                VALUES (?, ?, 'Cantante', 'ALTO', 'Posición', ?, ?, ?)
                """, versionId, singerId, new BigDecimal(x), new BigDecimal(y), heightCm);
    }

}
