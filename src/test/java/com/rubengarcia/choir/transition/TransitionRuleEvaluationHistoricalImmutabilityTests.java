package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;

import com.rubengarcia.choir.formation.application.usecase.FormationPlanningAuthorizationService;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TransitionRuleEvaluationHistoricalImmutabilityTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransitionRuleEvaluationHistoryPort evaluationHistoryPort;

    @Autowired
    private FormationPlanningAuthorizationService authorizedPlanningService;

    private Long directorId;
    private Long choirId;
    private Long concertId;
    private Long repertoireItemId;
    private Long singerOneId;
    private Long singerTwoId;
    private Long fromFormationId;
    private Long toFormationId;
    private Long fromVersionId;
    private Long toVersionId;
    private Long transitionId;
    private Long transitionVersionId;
    private Long sceneRuleId;
    private Long ruleRevisionId;
    private String email;

    @Test
    void laterRuleRevisionDoesNotChangePreviouslyPersistedEvaluation() {
        createFixture();

        TransitionRuleEvaluationSnapshot original =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();

        assertThat(original).isNotNull();
        assertThat(original.results()).isNotEmpty();
        assertThat(original.results())
                .extracting(TransitionRuleEvaluationSnapshot.RuleResult::status)
                .containsExactly(Status.SATISFIED);
        assertThat(original.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(original.results())
                .extracting(TransitionRuleEvaluationSnapshot.RuleResult::ruleRevisionId)
                .containsExactly(ruleRevisionId);


        var originalResults = original.results();
        var originalWarnings = original.warnings();
        var originalStatus = original.status();

        jdbc.update("""
                INSERT INTO scene_rule_revisions (
                    scene_rule_id, revision_number, repertoire_item_id,
                    rule_order, rule_type, description, active, revision_kind,
                    condition_json, definition_status
                )
                SELECT scene_rule_id, 2, repertoire_item_id, rule_order,
                       rule_type, description, FALSE, 'DEACTIVATE',
                       condition_json, definition_status
                FROM scene_rule_revisions
                WHERE id = ?
                """, ruleRevisionId);

        TransitionRuleEvaluationSnapshot later =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();

        assertThat(later).isNotNull();
        assertThat(later.id()).isNotEqualTo(original.id());
        assertThat(later.results()).isEmpty();
        assertThat(later.status()).isEqualTo(Status.NOT_EVALUABLE);

        TransitionRuleEvaluationSnapshot recovered =
                evaluationHistoryPort.findById(original.id()).block();

        assertThat(recovered).isNotNull();
        assertThat(recovered.id()).isEqualTo(original.id());
        assertThat(recovered.status()).isEqualTo(originalStatus);
        assertThat(recovered.results()).isEqualTo(originalResults);
        assertThat(recovered.warnings()).isEqualTo(originalWarnings);
        assertThat(recovered.results())
                .extracting(TransitionRuleEvaluationSnapshot.RuleResult::ruleRevisionId)
                .containsExactly(ruleRevisionId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM transition_rule_evaluation_results
                WHERE evaluation_id = ?
                """, Integer.class, original.id()))
                .isEqualTo(originalResults.size());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM transition_rule_evaluation_results
                WHERE evaluation_id = ?
                """, Integer.class, later.id()))
                .isZero();
    }

    @AfterEach
    void cleanup() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (connection.getMetaData().getDatabaseProductName()
                    .contains("PostgreSQL")) {
                return;
            }
        }

        if (transitionVersionId != null) {
            jdbc.update("""
                    DELETE FROM transition_rule_evaluation_warnings
                    WHERE evaluation_id IN (
                        SELECT id FROM transition_rule_evaluations
                        WHERE transition_version_id = ?)
                    """, transitionVersionId);
            jdbc.update("""
                    DELETE FROM transition_rule_evaluation_results
                    WHERE evaluation_id IN (
                        SELECT id FROM transition_rule_evaluations
                        WHERE transition_version_id = ?)
                    """, transitionVersionId);
            jdbc.update("""
                    DELETE FROM transition_rule_evaluations
                    WHERE transition_version_id = ?
                    """, transitionVersionId);
        }

        if (transitionId != null) {
            jdbc.update("DELETE FROM transition_versions WHERE transition_id = ?",
                    transitionId);
        }
        if (sceneRuleId != null) {
            jdbc.update("DELETE FROM scene_rule_revisions WHERE scene_rule_id = ?",
                    sceneRuleId);
            jdbc.update("DELETE FROM scene_rules WHERE id = ?", sceneRuleId);
        }
        if (transitionId != null) {
            jdbc.update("DELETE FROM transitions WHERE id = ?", transitionId);
        }
        if (fromVersionId != null) {
            jdbc.update("DELETE FROM formation_positions WHERE formation_version_id = ?",
                    fromVersionId);
            jdbc.update("DELETE FROM formation_versions WHERE id = ?", fromVersionId);
        }
        if (toVersionId != null) {
            jdbc.update("DELETE FROM formation_positions WHERE formation_version_id = ?",
                    toVersionId);
            jdbc.update("DELETE FROM formation_versions WHERE id = ?", toVersionId);
        }
        if (fromFormationId != null) {
            jdbc.update("DELETE FROM formations WHERE id = ?", fromFormationId);
        }
        if (toFormationId != null) {
            jdbc.update("DELETE FROM formations WHERE id = ?", toFormationId);
        }
        if (concertId != null) {
            jdbc.update("DELETE FROM concert_repertoire WHERE concert_id = ?", concertId);
            jdbc.update("DELETE FROM concerts WHERE id = ?", concertId);
        }
        if (repertoireItemId != null) {
            jdbc.update("DELETE FROM repertoire_items WHERE id = ?", repertoireItemId);
        }
        if (singerOneId != null) {
            jdbc.update("DELETE FROM singers WHERE id = ?", singerOneId);
        }
        if (singerTwoId != null) {
            jdbc.update("DELETE FROM singers WHERE id = ?", singerTwoId);
        }
        if (choirId != null) {
            jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
        }
        if (directorId != null) {
            jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
        }
    }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString();
        email = "historical-evaluation-" + suffix + "@example.test";

        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Evaluation director', ?)
                """, email);
        directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);

        jdbc.update("INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Evaluation choir " + suffix);
        choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?", Long.class, directorId);

        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Evaluation concert " + suffix);
        concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ?", Long.class, choirId);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Evaluation song " + suffix);
        repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ?",
                Long.class, choirId);

        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        singerOneId = insertSinger("Singer one " + suffix);
        singerTwoId = insertSinger("Singer two " + suffix);

        fromFormationId = insertFormation("From " + suffix);
        toFormationId = insertFormation("To " + suffix);
        fromVersionId = insertFormationVersion(fromFormationId);
        toVersionId = insertFormationVersion(toFormationId);

        insertPosition(fromVersionId, singerOneId, "0.10", "0.10");
        insertPosition(fromVersionId, singerTwoId, "0.20", "0.10");
        insertPosition(toVersionId, singerOneId, "0.10", "0.10");
        insertPosition(toVersionId, singerTwoId, "0.20", "0.10");

        jdbc.update("""
                INSERT INTO transitions (choir_id, name)
                VALUES (?, 'Historical evaluation transition')
                """, choirId);
        transitionId = jdbc.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ?",
                Long.class, choirId);

        jdbc.update("""
                INSERT INTO transition_versions (
                    transition_id, version_number,
                    from_formation_version_id, to_formation_version_id
                ) VALUES (?, 1, ?, ?)
                """, transitionId, fromVersionId, toVersionId);
        transitionVersionId = jdbc.queryForObject(
                "SELECT id FROM transition_versions WHERE transition_id = ?",
                Long.class, transitionId);

        String conditionJson = """
                {"spatialConstraints":[
                  {"relation":"NEAR","stage":"FROM",
                   "participants":[
                     {"type":"SINGER","singerId":%d},
                     {"type":"SINGER","singerId":%d}],
                   "threshold":0.5},
                  {"relation":"NEAR","stage":"TO",
                   "participants":[
                     {"type":"SINGER","singerId":%d},
                     {"type":"SINGER","singerId":%d}],
                   "threshold":0.5}
                ]}
                """.formatted(
                singerOneId, singerTwoId, singerOneId, singerTwoId);

        jdbc.update("""
                INSERT INTO scene_rules (
                    repertoire_item_id, rule_order, rule_type, description,
                    active, condition_json, definition_status
                ) VALUES (?, 0, 'MOVEMENT', 'Two-stage proximity',
                          TRUE, ?, 'READY')
                """, repertoireItemId, conditionJson);
        sceneRuleId = jdbc.queryForObject(
                "SELECT id FROM scene_rules WHERE repertoire_item_id = ?",
                Long.class, repertoireItemId);

        jdbc.update("""
                INSERT INTO scene_rule_revisions (
                    scene_rule_id, revision_number, repertoire_item_id,
                    rule_order, rule_type, description, active, revision_kind,
                    condition_json, definition_status
                ) VALUES (?, 1, ?, 0, 'MOVEMENT',
                          'Two-stage proximity', TRUE, 'CREATE', ?, 'READY')
                """, sceneRuleId, repertoireItemId, conditionJson);
        ruleRevisionId = jdbc.queryForObject(
                "SELECT id FROM scene_rule_revisions WHERE scene_rule_id = ?",
                Long.class, sceneRuleId);
    }

    private long insertSinger(String name) {
        jdbc.update("""
                INSERT INTO singers (choir_id, display_name, voice_part, active)
                VALUES (?, ?, 'SOPRANO', TRUE)
                """, choirId, name);
        return jdbc.queryForObject(
                "SELECT id FROM singers WHERE choir_id = ? AND display_name = ?",
                Long.class, choirId, name);
    }

    private long insertFormation(String name) {
        jdbc.update("""
                INSERT INTO formations (
                    choir_id, name, concert_id, repertoire_item_id
                ) VALUES (?, ?, ?, ?)
                """, choirId, name, concertId, repertoireItemId);
        return jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private long insertFormationVersion(long formationId) {
        jdbc.update("""
                INSERT INTO formation_versions (formation_id, version_number, status)
                VALUES (?, 1, 'DRAFT')
                """, formationId);
        return jdbc.queryForObject(
                "SELECT id FROM formation_versions WHERE formation_id = ?",
                Long.class, formationId);
    }

    private void insertPosition(
            long versionId, long singerId, String x, String y) {
        jdbc.update("""
                INSERT INTO formation_positions (
                    formation_version_id, singer_id, singer_name_snapshot,
                    voice_part_snapshot, position_label, x_coordinate, y_coordinate
                ) VALUES (?, ?, ?, 'SOPRANO', 'Evaluation position', ?, ?)
                """, versionId, singerId, "Singer " + singerId,
                new BigDecimal(x), new BigDecimal(y));
    }
}
