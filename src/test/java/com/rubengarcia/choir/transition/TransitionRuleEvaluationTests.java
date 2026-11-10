package com.rubengarcia.choir.transition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.formation.application.usecase.FormationPlanningAuthorizationService;
import com.rubengarcia.choir.formation.application.usecase.FormationVersionSelectionUseCase;
import com.rubengarcia.choir.scene.domain.SceneRuleEvaluator.Status;
import com.rubengarcia.choir.transition.application.model.TransitionMovementStepInput;
import com.rubengarcia.choir.transition.application.port.out.TransitionRuleEvaluationHistoryPort;
import com.rubengarcia.choir.transition.application.usecase.TransitionVersionLifecycleService;
import com.rubengarcia.choir.transition.domain.TransitionMovementStep;
import com.rubengarcia.choir.transition.domain.TransitionDisplacementMetrics;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import com.rubengarcia.choir.transition.domain.TransitionTrajectoryAnalysis;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransitionRuleEvaluationTests {

    private static final DisposablePostgresTestDatabase POSTGRES_TEST_DATABASE =
            new DisposablePostgresTestDatabase();

    @DynamicPropertySource
    static void isolatePostgresDatabase(DynamicPropertyRegistry registry) {
        POSTGRES_TEST_DATABASE.configure(registry);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WebTestClient web;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @AfterAll
    void dropPostgresTestDatabase() throws Exception {
        POSTGRES_TEST_DATABASE.drop();
    }

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
            jdbc.update("DELETE FROM transition_rule_evaluation_displacement_issues WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_singer_distances WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_displacement_metrics WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_trajectory_crossings WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_trajectory_issues WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_trajectory_analyses WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("""
                    DELETE FROM transition_rule_evaluation_movement_steps
                    WHERE evaluation_id IN (
                        SELECT id FROM transition_rule_evaluations
                        WHERE transition_version_id = ?
                    )
                    """, transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluation_results WHERE evaluation_id IN (SELECT id FROM transition_rule_evaluations WHERE transition_version_id = ?)", transitionVersionId);
            jdbc.update("DELETE FROM transition_rule_evaluations WHERE transition_version_id = ?", transitionVersionId);
            jdbc.update("DELETE FROM transition_movement_steps WHERE transition_version_id = ?", transitionVersionId);
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
        assertThat(first.displacementMetrics()).isNotNull();
        assertThat(first.displacementMetrics().transitionVersionId())
                .isEqualTo(transitionVersionId);
        assertThat(first.displacementMetrics().fromFormationVersionId())
                .isEqualTo(fromVersionId);
        assertThat(first.displacementMetrics().toFormationVersionId())
                .isEqualTo(toVersionId);
        assertThat(first.displacementMetrics().totalDistance())
                .isEqualByComparingTo("0.000000");
        assertThat(first.displacementMetrics())
                .isEqualTo(second.displacementMetrics());
        assertThat(first.trajectoryAnalysis()).isNotNull();
        assertThat(first.trajectoryAnalysis().transitionVersionId())
                .isEqualTo(transitionVersionId);
        assertThat(first.trajectoryAnalysis().crossings()).isEmpty();
        assertThat(first.trajectoryAnalysis())
                .isEqualTo(second.trajectoryAnalysis());
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
        assertThat(recovered.displacementMetrics())
                .isEqualTo(first.displacementMetrics());
        assertThat(recovered.trajectoryAnalysis())
                .isEqualTo(first.trajectoryAnalysis());
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
    void evaluationEndpointReturnsAndRecoversCompleteTransitionContract() {
        createFixture();
        jdbc.update("""
                UPDATE directors SET password_hash = ?, email_verified = TRUE
                WHERE id = ?
                """, passwordEncoder.encode("Transition-Evaluation-Test-2026!"), directorId);
        Session session = login();
        String path = "/api/choirs/" + choirId + "/transitions/" + transitionId
                + "/versions/" + transitionVersionId + "/rule-evaluations";

        web.post()
                .uri(path)
                .cookie("SESSION", session.id())
                .header("X-CSRF-TOKEN", session.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.transitionId").isEqualTo(transitionId)
                .jsonPath("$.transitionVersionId").isEqualTo(transitionVersionId)
                .jsonPath("$.fromFormationVersionId").isEqualTo(fromVersionId)
                .jsonPath("$.toFormationVersionId").isEqualTo(toVersionId)
                .jsonPath("$.repertoireItemId").isEqualTo(repertoireItemId)
                .jsonPath("$.displacementMetrics.movingSingerCount").isEqualTo(0)
                .jsonPath("$.trajectoryAnalysis.crossings").isArray();

        web.get()
                .uri(path)
                .cookie("SESSION", session.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].displacementMetrics.transitionVersionId")
                .isEqualTo(transitionVersionId)
                .jsonPath("$[0].trajectoryAnalysis.transitionVersionId")
                .isEqualTo(transitionVersionId)
                .jsonPath("$[0].results[0].ruleRevisionId")
                .isEqualTo(ruleRevisionId);
    }

    @Test
    void integratesCrossingConflictAndPersistsExactFunctionalEvidence() {
        createFixture();
        jdbc.update("""
                UPDATE formation_positions SET x_coordinate = 0, y_coordinate = 0
                WHERE formation_version_id = ? AND singer_id = ?
                """, fromVersionId, singerOneId);
        jdbc.update("""
                UPDATE formation_positions SET x_coordinate = 0, y_coordinate = 1
                WHERE formation_version_id = ? AND singer_id = ?
                """, fromVersionId, singerTwoId);
        jdbc.update("""
                UPDATE formation_positions SET x_coordinate = 1, y_coordinate = 1
                WHERE formation_version_id = ? AND singer_id = ?
                """, toVersionId, singerOneId);
        jdbc.update("""
                UPDATE formation_positions SET x_coordinate = 1, y_coordinate = 0
                WHERE formation_version_id = ? AND singer_id = ?
                """, toVersionId, singerTwoId);
        authorizedPlanningService.replaceMovementSteps(
                email,
                choirId,
                transitionId,
                transitionVersionId,
                List.of(
                        movementInput(1, singerOneId, "2026-01-01T10:00:00", "MOVE"),
                        movementInput(1, singerTwoId, "2026-01-01T10:00:00", "MOVE")))
                .block();

        TransitionRuleEvaluationSnapshot evaluation =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();

        assertThat(evaluation.status()).isEqualTo(Status.VIOLATED);
        assertThat(evaluation.displacementMetrics().movingSingerCount()).isEqualTo(2);
        assertThat(evaluation.displacementMetrics().totalDistance())
                .isEqualByComparingTo("2.828428");
        assertThat(evaluation.trajectoryAnalysis().crossings())
                .singleElement()
                .satisfies(crossing -> {
                    assertThat(crossing.firstSingerId()).isEqualTo(singerOneId);
                    assertThat(crossing.secondSingerId()).isEqualTo(singerTwoId);
                    assertThat(crossing.temporalConflictStatus().name())
                            .isEqualTo("CONFIRMED");
                });
        assertThat(evaluation.warnings())
                .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                .contains("TRAJECTORY_CONFLICT_CONFIRMED");
        assertThat(evaluation.results()).singleElement().satisfies(result -> {
            assertThat(result.ruleRevisionId()).isEqualTo(ruleRevisionId);
            assertThat(result.status()).isEqualTo(Status.VIOLATED);
        });
        TransitionRuleEvaluationSnapshot recovered =
                evaluationHistoryPort.findById(evaluation.id()).block();
        assertThat(recovered.displacementMetrics())
                .isEqualTo(evaluation.displacementMetrics());
        assertThat(recovered.trajectoryAnalysis())
                .isEqualTo(evaluation.trajectoryAnalysis());
        assertThat(recovered.results().getFirst().ruleRevisionId())
                .isEqualTo(ruleRevisionId);
    }

    @Test
    void incompleteSnapshotsAreReportedAndRemainImmutableInHistory() {
        createFixture();
        jdbc.update("""
                DELETE FROM formation_positions
                WHERE formation_version_id = ? AND singer_id = ?
                """, toVersionId, singerTwoId);

        TransitionRuleEvaluationSnapshot evaluation =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();
        assertThat(evaluation.results()).singleElement().satisfies(result -> {
            assertThat(result.ruleRevisionId()).isEqualTo(ruleRevisionId);
            assertThat(result.status()).isEqualTo(Status.NOT_EVALUABLE);
        });
        assertThat(evaluation.displacementMetrics().dataIssues())
                .anySatisfy(issue -> {
                    assertThat(issue.code())
                            .isEqualTo(TransitionDisplacementMetrics.DataIssueCode
                                    .MISSING_COUNTERPART);
                    assertThat(issue.stage())
                            .isEqualTo(TransitionDisplacementMetrics.Stage.TO);
                    assertThat(issue.singerId()).isEqualTo(singerTwoId);
                });
        assertThat(evaluation.trajectoryAnalysis().geometryIssues())
                .anySatisfy(issue -> {
                    assertThat(issue.code())
                            .isEqualTo(TransitionTrajectoryAnalysis.GeometryIssueCode
                                    .MISSING_COUNTERPART);
                    assertThat(issue.stage()).isEqualTo("TO");
                    assertThat(issue.singerId()).isEqualTo(singerTwoId);
                });
        assertThat(evaluation.warnings())
                .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                .contains("DISPLACEMENT_MISSING_COUNTERPART",
                        "TRAJECTORY_MISSING_COUNTERPART");

        jdbc.update("""
                INSERT INTO formation_positions (
                    formation_version_id, singer_id, singer_name_snapshot,
                    voice_part_snapshot, position_label, x_coordinate, y_coordinate
                ) VALUES (?, ?, 'Restored singer', 'SOPRANO', 'Restored', 0.9, 0.9)
                """, toVersionId, singerTwoId);
        TransitionRuleEvaluationSnapshot recovered =
                evaluationHistoryPort.findById(evaluation.id()).block();
        assertThat(recovered.displacementMetrics())
                .isEqualTo(evaluation.displacementMetrics());
        assertThat(recovered.trajectoryAnalysis())
                .isEqualTo(evaluation.trajectoryAnalysis());
    }

    @Test
    void activeRuleViolationPersistsItsExactRevisionAndActionableWarning() {
        createFixture();
        jdbc.update("""
                UPDATE formation_positions
                SET x_coordinate = 0.90
                WHERE formation_version_id = ? AND singer_id = ?
                """, toVersionId, singerTwoId);

        TransitionRuleEvaluationSnapshot evaluation =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();

        assertThat(evaluation).isNotNull();
        assertThat(evaluation.status()).isEqualTo(Status.VIOLATED);
        assertThat(evaluation.results()).singleElement().satisfies(result -> {
            assertThat(result.sceneRuleId()).isEqualTo(sceneRuleId);
            assertThat(result.ruleRevisionId()).isEqualTo(ruleRevisionId);
            assertThat(result.status()).isEqualTo(Status.VIOLATED);
            assertThat(result.warnings())
                    .extracting(TransitionRuleEvaluationSnapshot.Warning::code)
                    .containsExactly("NEAR_THRESHOLD_EXCEEDED");
            assertThat(result.warnings().getFirst().explanation())
                    .isEqualTo("La distancia supera el umbral");
        });

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM transition_rule_evaluation_results
                WHERE evaluation_id = ?
                  AND scene_rule_id = ?
                  AND rule_revision_id = ?
                  AND status = 'VIOLATED'
                """, Integer.class, evaluation.id(), sceneRuleId, ruleRevisionId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM transition_rule_evaluation_warnings
                WHERE evaluation_id = ?
                  AND result_order = 0
                  AND warning_order = 0
                  AND code = 'NEAR_THRESHOLD_EXCEEDED'
                  AND explanation = 'La distancia supera el umbral'
                  AND status = 'VIOLATED'
                """, Integer.class, evaluation.id()))
                .isEqualTo(1);
    }

    @Test
    void capturesAndEvaluatesTemporalStepsAndKeepsHistoricalEvidenceImmutable() {
        createFixture();
        installSequentialMovementRule();
        List<TransitionMovementStepInput> capturedInput = List.of(
                movementInput(1, singerOneId, "2026-01-01T10:00:00", "ENTER"),
                movementInput(2, singerTwoId, "2026-01-01T10:00:01", "TURN"));

        List<TransitionMovementStep> captured = authorizedPlanningService
                .replaceMovementSteps(
                        email, choirId, transitionId, transitionVersionId, capturedInput)
                .block();

        assertThat(captured).hasSize(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_movement_steps
                WHERE transition_version_id = ?
                """, Integer.class, transitionVersionId))
                .isEqualTo(2);

        TransitionRuleEvaluationSnapshot evaluation =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.results()).singleElement().satisfies(result ->
                assertThat(result.status()).isEqualTo(Status.SATISFIED));
        assertThat(evaluation.movementSteps()).containsExactlyElementsOf(captured);

        TransitionRuleEvaluationSnapshot recovered =
                evaluationHistoryPort.findById(evaluation.id()).block();
        assertThat(recovered.movementSteps()).containsExactlyElementsOf(captured);

        List<TransitionMovementStep> replacement = authorizedPlanningService
                .replaceMovementSteps(
                        email,
                        choirId,
                        transitionId,
                        transitionVersionId,
                        List.of(
                                movementInput(1, singerOneId, "2026-01-01T10:00:00", "TURN"),
                                movementInput(2, singerTwoId, "2026-01-01T10:00:01", "ENTER")))
                .block();
        assertThat(replacement).hasSize(2);
        assertThat(evaluationHistoryPort.findById(evaluation.id()).block()
                .movementSteps())
                .containsExactlyElementsOf(captured);

        TransitionRuleEvaluationSnapshot replacementEvaluation =
                authorizedPlanningService.evaluateTransitionVersion(
                        email, choirId, transitionId, transitionVersionId)
                        .block();
        assertThat(replacementEvaluation.results()).singleElement().satisfies(result ->
                assertThat(result.status()).isEqualTo(Status.VIOLATED));
        assertThat(replacementEvaluation.movementSteps())
                .containsExactlyElementsOf(replacement);
    }

    @Test
    void evaluationMovementEvidenceRollsBackWhenSnapshotPersistenceFails() {
        createFixture();
        TransitionRuleEvaluationSnapshot invalid = new TransitionRuleEvaluationSnapshot(
                null,
                transitionVersionId,
                transitionId,
                fromVersionId,
                toVersionId,
                repertoireItemId,
                1,
                new BigDecimal("0.05"),
                Status.NOT_EVALUABLE,
                LocalDateTime.now(),
                List.of(),
                List.of(),
                List.of(new TransitionMovementStep(
                        transitionId,
                        transitionVersionId + 999,
                        fromVersionId,
                        toVersionId,
                        singerOneId,
                        1,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                        "ENTER")));

        assertThatThrownBy(() -> evaluationHistoryPort.save(invalid).block())
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Integer.class, transitionVersionId))
                .isZero();
    }

    @Test
    void publicationRequiresCurrentMovementEvidenceAndFreezesPublishedSteps() {
        createFixture();
        List<TransitionMovementStep> firstSteps = authorizedPlanningService
                .replaceMovementSteps(
                        email,
                        choirId,
                        transitionId,
                        transitionVersionId,
                        List.of(movementInput(
                                1, singerOneId, "2026-01-01T10:00:00", "ENTER")))
                .block();
        TransitionRuleEvaluationSnapshot stale = evaluationWithMovementSteps(firstSteps);
        TransitionRuleEvaluationSnapshot savedStale =
                evaluationHistoryPort.save(stale).block();

        List<TransitionMovementStep> currentSteps = authorizedPlanningService
                .replaceMovementSteps(
                        email,
                        choirId,
                        transitionId,
                        transitionVersionId,
                        List.of(movementInput(
                                1, singerOneId, "2026-01-01T10:00:00", "TURN")))
                .block();
        assertThatThrownBy(() -> lifecycleService.publish(
                email,
                choirId,
                transitionId,
                transitionVersionId,
                savedStale.id(),
                List.of()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("movimientos cambiaron");

        TransitionRuleEvaluationSnapshot current =
                evaluationHistoryPort.save(evaluationWithMovementSteps(currentSteps))
                        .block();
        TransitionVersion published = lifecycleService.publish(
                email,
                choirId,
                transitionId,
                transitionVersionId,
                current.id(),
                List.of()).block();

        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> authorizedPlanningService.replaceMovementSteps(
                email,
                choirId,
                transitionId,
                transitionVersionId,
                List.of(movementInput(
                        1, singerOneId, "2026-01-01T10:00:00", "ENTER")))
                .block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DRAFT");
        assertThat(evaluationHistoryPort.findById(current.id()).block()
                .movementSteps())
                .containsExactlyElementsOf(currentSteps);
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
        jdbc.update("""
                INSERT INTO transition_rule_evaluation_results (
                    evaluation_id, result_order, scene_rule_id,
                    rule_revision_id, rule_type, status, code, explanation
                ) VALUES (?, 0, ?, ?, 'MOVEMENT', 'SATISFIED',
                          'RULE_SATISFIED', 'Todos los criterios se cumplen')
                """, evaluationId, sceneRuleId, ruleRevisionId);

        TransitionVersion published = lifecycleService.publish(
                email,
                choirId,
                transitionId,
                transitionVersionId,
                evaluationId,
                List.of())
                .block(java.time.Duration.ofSeconds(10));

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

        assertThat(jdbc.queryForMap("""
                SELECT result.scene_rule_id, result.rule_revision_id
                FROM transition_publication_evidence evidence
                JOIN transition_rule_evaluation_results result
                  ON result.evaluation_id = evidence.evaluation_id
                WHERE evidence.transition_version_id = ?
                """, transitionVersionId))
                .containsEntry("SCENE_RULE_ID", sceneRuleId)
                .containsEntry("RULE_REVISION_ID", ruleRevisionId);
        assertThat(jdbc.queryForObject("""
                SELECT active FROM scene_rule_revisions WHERE id = ?
                """, Boolean.class, jdbc.queryForObject("""
                SELECT id FROM scene_rule_revisions
                WHERE scene_rule_id = ? AND revision_number = 2
                """, Long.class, sceneRuleId)))
                .isFalse();

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

    private void installSequentialMovementRule() {
        String conditionJson = """
                {"orderingMode":"SEQUENTIAL","expectedMovementSteps":[
                  {"order":1,"participants":[
                    {"type":"SINGER","singerId":%d}],"action":"ENTER"},
                  {"order":2,"participants":[
                    {"type":"SINGER","singerId":%d}],"action":"TURN"}
                ]}
                """.formatted(singerOneId, singerTwoId);
        jdbc.update("""
                UPDATE scene_rules
                SET condition_json = ?, definition_status = 'READY'
                WHERE id = ?
                """, conditionJson, sceneRuleId);
        jdbc.update("""
                UPDATE scene_rule_revisions
                SET condition_json = ?, definition_status = 'READY'
                WHERE id = ?
                """, conditionJson, ruleRevisionId);
    }

    private TransitionMovementStepInput movementInput(
            int sequenceNumber,
            long singerId,
            String temporalReference,
            String action) {
        return new TransitionMovementStepInput(
                singerId,
                sequenceNumber,
                OffsetDateTime.parse(temporalReference + "Z"),
                action);
    }

    private TransitionRuleEvaluationSnapshot evaluationWithMovementSteps(
            List<TransitionMovementStep> movementSteps) {
        return new TransitionRuleEvaluationSnapshot(
                null,
                transitionVersionId,
                transitionId,
                fromVersionId,
                toVersionId,
                repertoireItemId,
                1,
                new BigDecimal("0.05"),
                Status.SATISFIED,
                LocalDateTime.now(),
                List.of(),
                List.of(),
                movementSteps);
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
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Evaluation choir " + suffix);
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, choirId, "Evaluation concert " + suffix);
        concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Evaluation concert " + suffix);
        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, choirId, "Evaluation song " + suffix);
        repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, choirId, "Evaluation song " + suffix);
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
                "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Rule evaluation transition");
        jdbc.update("""
                INSERT INTO transition_versions (
                    transition_id, version_number,
                    from_formation_version_id, to_formation_version_id
                ) VALUES (?, 1, ?, ?)
                """, transitionId, fromVersionId, toVersionId);
        transitionVersionId = jdbc.queryForObject(
                """
                SELECT id FROM transition_versions
                WHERE transition_id = ? AND version_number = 1
                """,
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
                """
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
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

    private Session login() {
        var csrfResponse = web.get()
                .uri("/api/auth/csrf")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, String>>() {})
                .returnResult();

        ResponseCookie initialCookie =
                csrfResponse.getResponseCookies().getFirst("SESSION");
        assertThat(initialCookie).isNotNull();
        String csrf = csrfResponse.getResponseBody().get("token");
        assertThat(csrf).isNotBlank();

        var loginResponse = web.post()
                .uri("/api/auth/login")
                .cookie("SESSION", initialCookie.getValue())
                .header("X-CSRF-TOKEN", csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", email,
                        "password", "Transition-Evaluation-Test-2026!"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        ResponseCookie authenticatedCookie =
                loginResponse.getResponseCookies().getFirst("SESSION");
        assertThat(authenticatedCookie).isNotNull();
        return new Session(authenticatedCookie.getValue(), csrf);
    }

    private record Session(String id, String csrf) {
    }

}
