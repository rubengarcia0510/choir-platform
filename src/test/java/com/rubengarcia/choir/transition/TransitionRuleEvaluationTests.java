package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.formation.application.usecase.FormationPlanningAuthorizationService;
import com.rubengarcia.choir.formation.application.usecase.FormationVersionSelectionUseCase;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.transition.application.usecase.TransitionVersionLifecycleService;
import org.springframework.dao.DataAccessException;
import org.junit.jupiter.api.Assumptions;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class TransitionRuleEvaluationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransitionRuleEvaluationHistoryPort evaluationHistoryPort;



    @Autowired
    private FormationPlanningAuthorizationService authorizedPlanningService;

    @Autowired
    private FormationVersionSelectionUseCase formationVersionSelectionUseCase;

    @Autowired
    private TransitionVersionLifecycleService lifecycleService;

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
    private Long selectedFormationId;
    private Long selectedVersionId;
    private Long transitionId;
    private Long transitionVersionId;
    private Long sceneRuleId;
    private Long ruleRevisionId;
    private String email;

    @AfterEach
    void cleanup() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName();
            if (database.contains("PostgreSQL")) {
                return;
            }
        }

        if (transitionVersionId != null) {
            jdbc.update("""
                    DELETE FROM transition_publication_decisions
                    WHERE transition_version_id = ?
                    """, transitionVersionId);
            jdbc.update("""
                    DELETE FROM transition_publication_evidence
                    WHERE transition_version_id = ?
                    """, transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_warnings WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_results WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluations WHERE transition_version_id = ?", transitionVersionId);
        }
        if (transitionId != null) jdbc.update("DELETE FROM transition_versions WHERE transition_id = ?", transitionId);
        if (sceneRuleId != null) jdbc.update("DELETE FROM scene_rule_revisions WHERE scene_rule_id = ?", sceneRuleId);
        if (sceneRuleId != null) jdbc.update("DELETE FROM scene_rules WHERE id = ?", sceneRuleId);
        if (transitionId != null) jdbc.update("DELETE FROM transitions WHERE id = ?", transitionId);
        if (concertId != null && repertoireItemId != null) {
            jdbc.update("""
                    DELETE FROM formation_version_selection_history
                    WHERE concert_id = ? AND repertoire_item_id = ?
                    """, concertId, repertoireItemId);
            jdbc.update("""
                    UPDATE concert_repertoire
                    SET selected_formation_id = NULL,
                        selected_formation_version_id = NULL
                    WHERE concert_id = ? AND repertoire_item_id = ?
                    """, concertId, repertoireItemId);
        }
        if (fromVersionId != null || toVersionId != null) {
            if (fromVersionId != null) jdbc.update("DELETE FROM formation_positions WHERE formation_version_id = ?", fromVersionId);
            if (toVersionId != null) jdbc.update("DELETE FROM formation_positions WHERE formation_version_id = ?", toVersionId);
            if (fromVersionId != null) jdbc.update("DELETE FROM formation_versions WHERE id = ?", fromVersionId);
            if (toVersionId != null) jdbc.update("DELETE FROM formation_versions WHERE id = ?", toVersionId);
        }
        if (selectedVersionId != null) {
            jdbc.update("DELETE FROM formation_versions WHERE id = ?", selectedVersionId);
        }
        if (fromFormationId != null) jdbc.update("DELETE FROM formations WHERE id = ?", fromFormationId);
        if (toFormationId != null) jdbc.update("DELETE FROM formations WHERE id = ?", toFormationId);
        if (selectedFormationId != null) {
            jdbc.update("DELETE FROM formations WHERE id = ?", selectedFormationId);
        }
        if (concertId != null) jdbc.update("DELETE FROM concert_repertoire WHERE concert_id = ?", concertId);
        if (concertId != null) jdbc.update("DELETE FROM concerts WHERE id = ?", concertId);
        if (repertoireItemId != null) jdbc.update("DELETE FROM repertoire_items WHERE id = ?", repertoireItemId);
        if (singerOneId != null) jdbc.update("DELETE FROM singers WHERE id = ?", singerOneId);
        if (singerTwoId != null) jdbc.update("DELETE FROM singers WHERE id = ?", singerTwoId);
        if (choirId != null) jdbc.update("DELETE FROM choirs WHERE id = ?", choirId);
        if (directorId != null) jdbc.update("DELETE FROM directors WHERE id = ?", directorId);
    }

    @Test
    void recalculationsPersistSeparateExactSnapshotsAndWarnings() {
        createFixture();

        TransitionRuleEvaluationSnapshot first = authorizedPlanningService
                .evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                .block();
        TransitionRuleEvaluationSnapshot second = authorizedPlanningService
                .evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                .block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.transitionVersionId()).isEqualTo(transitionVersionId);
        assertThat(first.fromFormationVersionId()).isEqualTo(fromVersionId);
        assertThat(first.toFormationVersionId()).isEqualTo(toVersionId);
        assertThat(first.repertoireItemId()).isEqualTo(repertoireItemId);
        assertThat(first.results()).hasSize(1);
        assertThat(first.results().getFirst().ruleRevisionId())
                .isEqualTo(ruleRevisionId);
        assertThat(first.results().getFirst().status()).isEqualTo(Status.SATISFIED);
        assertThat(first.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(first.warnings())
                .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                .containsExactly("DIRECTOR_VISIBILITY_NOT_EVALUABLE");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Integer.class, transitionVersionId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluation_results
                WHERE evaluation_id IN (?, ?)
                """, Integer.class, first.id(), second.id()))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluation_warnings
                WHERE evaluation_id IN (?, ?)
                  AND code = 'DIRECTOR_VISIBILITY_NOT_EVALUABLE'
                """, Integer.class, first.id(), second.id()))
                .isEqualTo(2);
        List<TransitionRuleEvaluationSnapshot> history = authorizedPlanningService
                .transitionRuleEvaluationHistory(
                        email, choirId, transitionId, transitionVersionId)
                .collectList()
                .block();
        assertThat(history)
                .extracting(TransitionRuleEvaluationSnapshot::id)
                .containsExactly(first.id(), second.id());
        assertThat(history.getFirst().results().getFirst().ruleRevisionId())
                .isEqualTo(ruleRevisionId);
        assertThat(history.getFirst().warnings())
                .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                .containsExactly("DIRECTOR_VISIBILITY_NOT_EVALUABLE");

        TransitionRuleEvaluationSnapshot recovered =
                evaluationHistoryPort.findById(first.id()).block();
        assertThat(recovered).isNotNull();
        assertThat(recovered.id()).isEqualTo(first.id());
        assertThat(recovered.transitionVersionId()).isEqualTo(transitionVersionId);
        assertThat(recovered.results())
                .extracting(TransitionRuleEvaluationSnapshot.RuleResult::ruleRevisionId)
                .containsExactly(ruleRevisionId);
        assertThat(recovered.warnings())
                .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                .containsExactly("DIRECTOR_VISIBILITY_NOT_EVALUABLE");

        assertThat(evaluationHistoryPort.findById(Long.MAX_VALUE).block())
                .isNull();
        assertThatThrownBy(() -> authorizedPlanningService.evaluateTransitionVersion(
                "not-the-owner@example.test",
                choirId,
                transitionId,
                transitionVersionId)
                .block())
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> authorizedPlanningService.evaluateTransitionVersion(
                email,
                choirId,
                transitionId + 1,
                transitionVersionId)
                .block())
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Integer.class, transitionVersionId))
                .isEqualTo(2);
    }

    @Test
    void inactiveLatestRevisionDoesNotFallBackToEarlierActiveRevision() {
        createFixture();

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

        TransitionRuleEvaluationSnapshot evaluation = authorizedPlanningService
                .evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                .block();

        assertThat(evaluation).isNotNull();
        assertThat(evaluation.results()).isEmpty();
        assertThat(evaluation.status()).isEqualTo(Status.NOT_EVALUABLE);
        assertThat(evaluation.warnings())
                .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                .containsExactly("DIRECTOR_VISIBILITY_NOT_EVALUABLE");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluation_results
                WHERE evaluation_id = ?
                """, Integer.class, evaluation.id()))
                .isZero();
    }

    @Test
    void postgresPublicationHistoryRejectsUpdatesAndDeletes() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            Assumptions.assumeTrue(
                    connection.getMetaData().getDatabaseProductName().contains("PostgreSQL"),
                    "Esta prueba requiere PostgreSQL para validar los triggers de V25");
        }

        createFixture();
        TransitionRuleEvaluationSnapshot evaluation = authorizedPlanningService
                .evaluateTransitionVersion(email, choirId, transitionId, transitionVersionId)
                .block();
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.results()).isNotEmpty();
        assertThat(evaluation.warnings()).isNotEmpty();

        TransitionVersion draft = lifecycleService.createDraft(
                email, choirId, transitionId, fromVersionId, toVersionId,
                "CHOIR-94 validación de inmutabilidad PostgreSQL")
                .block(java.time.Duration.ofSeconds(10));
        String title = "CHOIR-94 immutable history " + UUID.randomUUID();
        jdbc.update("INSERT INTO repertoire_items (choir_id, title) VALUES (?, ?)",
                choirId, title);
        Long repertoireId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, title);
        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'VIOLATED')
                """, draft.getId(), transitionId, fromVersionId, toVersionId, repertoireId);
        Long publicationEvaluationId = jdbc.queryForObject("""
                SELECT MAX(id) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Long.class, draft.getId());
        jdbc.update("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, warning_order,
                    code, explanation, status
                ) VALUES (?, NULL, 0, 'STAGE_SEPARATION',
                          'Excepción documentada para probar el historial', 'VIOLATED')
                """, publicationEvaluationId);
        lifecycleService.publish(email, choirId, transitionId, draft.getId(),
                publicationEvaluationId,
                java.util.List.of(new com.rubengarcia.choir.transition.application.port.out
                        .TransitionVersionPublicationPort.WarningAcceptance(
                                -1, 0, "Aceptación explícita para prueba de inmutabilidad")))
                .block(java.time.Duration.ofSeconds(10));

        Object[][] mutations = {
            {"transition_rule_evaluations", "id", evaluation.id()},
            {"transition_rule_evaluation_results", "evaluation_id", evaluation.id()},
            {"transition_rule_evaluation_warnings", "evaluation_id", evaluation.id()},
            {"transition_publication_evidence", "transition_version_id", draft.getId()},
            {"transition_publication_decisions", "transition_version_id", draft.getId()}
        };
        for (Object[] mutation : mutations) {
            String table = (String) mutation[0];
            String keyColumn = (String) mutation[1];
            Object key = mutation[2];
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE " + keyColumn + " = ?",
                    Integer.class, key))
                    .as("Debe existir una fila de prueba en %s", table)
                    .isPositive();
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE " + table + " SET " + keyColumn + " = " + keyColumn
                            + " WHERE " + keyColumn + " = ?", key))
                    .as("PostgreSQL debe rechazar UPDATE en %s", table)
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "DELETE FROM " + table + " WHERE " + keyColumn + " = ?", key))
                    .as("PostgreSQL debe rechazar DELETE en %s", table)
                    .isInstanceOf(DataAccessException.class);
        }

        assertThatThrownBy(() -> jdbc.update("""
                UPDATE transition_versions
                SET instructions = instructions
                WHERE id = ?
                """, draft.getId()))
                .as("PostgreSQL debe rechazar UPDATE de una transición publicada")
                .isInstanceOf(DataAccessException.class);

        jdbc.update("""
                INSERT INTO transition_versions (
                    transition_id, version_number,
                    from_formation_version_id, to_formation_version_id,
                    status, published_at
                ) VALUES (?, 3, ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP)
                """, transitionId, fromVersionId, toVersionId);
        Long publishedVersionWithoutEvidence = jdbc.queryForObject("""
                SELECT id FROM transition_versions
                WHERE transition_id = ? AND version_number = 3
                """, Long.class, transitionId);

        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM transition_versions WHERE id = ?
                """, publishedVersionWithoutEvidence))
                .as("PostgreSQL debe rechazar DELETE de una transición publicada")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laterFormationSelectionDoesNotChangePublishedTransitionHistory() {
        createFixture();

        selectedFormationId = insertFormation("Selected after publication");
        selectedVersionId = insertFormationVersion(selectedFormationId);
        jdbc.update("""
                UPDATE formation_versions
                SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, selectedVersionId);

        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'SATISFIED')
                """,
                transitionVersionId,
                transitionId,
                fromVersionId,
                toVersionId,
                repertoireItemId);
        Long evaluationId = jdbc.queryForObject("""
                SELECT id FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Long.class, transitionVersionId);

        TransitionVersion published = lifecycleService.publish(
                email,
                choirId,
                transitionId,
                transitionVersionId,
                evaluationId,
                List.of())
                .block(java.time.Duration.ofSeconds(10));

        assertThat(published).isNotNull();
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");

        formationVersionSelectionUseCase.selectPublishedVersion(
                email,
                choirId,
                concertId,
                repertoireItemId,
                selectedFormationId,
                selectedVersionId)
                .block(java.time.Duration.ofSeconds(10));

        assertThat(jdbc.queryForObject("""
                SELECT selected_formation_version_id
                FROM concert_repertoire
                WHERE concert_id = ? AND repertoire_item_id = ?
                """, Long.class, concertId, repertoireItemId))
                .isEqualTo(selectedVersionId);

        assertThat(jdbc.queryForMap("""
                SELECT from_formation_version_id, to_formation_version_id
                FROM transition_versions
                WHERE id = ?
                """, transitionVersionId))
                .containsEntry("FROM_FORMATION_VERSION_ID", fromVersionId)
                .containsEntry("TO_FORMATION_VERSION_ID", toVersionId);

        assertThat(jdbc.queryForMap("""
                SELECT transition_id, from_formation_version_id,
                       to_formation_version_id
                FROM transition_publication_evidence
                WHERE transition_version_id = ?
                """, transitionVersionId))
                .containsEntry("TRANSITION_ID", transitionId)
                .containsEntry("FROM_FORMATION_VERSION_ID", fromVersionId)
                .containsEntry("TO_FORMATION_VERSION_ID", toVersionId);

        TransitionRuleEvaluationSnapshot recoveredEvaluation =
                evaluationHistoryPort.findById(evaluationId)
                        .block(java.time.Duration.ofSeconds(10));
        assertThat(recoveredEvaluation).isNotNull();
        assertThat(recoveredEvaluation.fromFormationVersionId())
                .isEqualTo(fromVersionId);
        assertThat(recoveredEvaluation.toFormationVersionId())
                .isEqualTo(toVersionId);

        assertThat(authorizedPlanningService
                .positionsForTransitionVersion(
                        email, choirId, transitionVersionId)
                .collectList()
                .block(java.time.Duration.ofSeconds(10)))
                .extracting(position ->
                        position.stage() + ":" + position.formationVersionId())
                .containsExactlyInAnyOrder(
                        "FROM:" + fromVersionId,
                        "FROM:" + fromVersionId,
                        "TO:" + toVersionId,
                        "TO:" + toVersionId);
    }

    private void createFixture() {
        String suffix = UUID.randomUUID().toString();
        email = "rule-evaluation-" + suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Evaluation director', ?)
                """, email);
        directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?", Long.class, email);
        jdbc.update(
                "INSERT INTO choirs (director_id, name) VALUES (?, ?)",
                directorId, "Evaluation choir " + suffix);
        choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ?",
                Long.class, directorId);
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Evaluation concert " + suffix);
        concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ?",
                Long.class, choirId);
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
                VALUES (?, 'Rule evaluation transition')
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
                INSERT INTO scene_rules
                    (repertoire_item_id, rule_order, rule_type, description,
                     active, condition_json, definition_status)
                VALUES (?, 0, 'MOVEMENT', 'Two-stage proximity', TRUE, ?, 'READY')
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
