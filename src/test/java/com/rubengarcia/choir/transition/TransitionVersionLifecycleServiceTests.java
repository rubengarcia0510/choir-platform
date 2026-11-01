package com.rubengarcia.choir.transition;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.transition.application.usecase.TransitionVersionLifecycleService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransitionVersionLifecycleServiceTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransitionVersionLifecycleService service;

    @Test
    void createsAndListsDraftsWithIncreasingVersionNumbers() {
        Fixture fixture = fixture();

        TransitionVersion first = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Indicaciones iniciales")
                .block(TIMEOUT);
        TransitionVersion second = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                null)
                .block(TIMEOUT);

        assertThat(first).isNotNull();
        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo("DRAFT");
        assertThat(first.getInstructions()).isEqualTo("Indicaciones iniciales");
        assertThat(first.getFromFormationVersionId())
                .isEqualTo(fixture.fromFormationVersionId());
        assertThat(first.getToFormationVersionId())
                .isEqualTo(fixture.toFormationVersionId());
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(service.list(
                fixture.email(), fixture.choirId(), fixture.transitionId())
                .collectList().block(TIMEOUT))
                .extracting(TransitionVersion::getVersionNumber)
                .containsExactly(2, 1);
    }

    @Test
    void rejectsFormationVersionsFromAnotherChoir() {
        Fixture fixture = fixture();
        long foreignFormationVersionId = foreignFormationVersion();

        assertThatThrownBy(() -> service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                foreignFormationVersionId,
                "No debe guardarse")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no pertenecen");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE transition_id = ?
                """, Integer.class, fixture.transitionId())).isZero();
    }

    @Test
    void rejectsUsingTheSameFormationVersionOnBothSides() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.fromFormationVersionId(),
                "Origen y destino iguales")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE transition_id = ?
                """, Integer.class, fixture.transitionId())).isZero();
    }

    @Test
    void rejectsAccessFromAnotherDirector() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.createDraft(
                "otro-director@example.test",
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "No autorizado")
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE transition_id = ?
                """, Integer.class, fixture.transitionId())).isZero();
    }

    @Test
    void rejectsListingAnotherDirectorsTransition() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> service.list(
                "otro-director@example.test",
                fixture.choirId(),
                fixture.transitionId())
                .collectList().block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsListingTransitionOutsideTheRequestedChoir() {
        Fixture fixture = fixture();
        long otherChoirId = createOtherChoir();

        assertThatThrownBy(() -> service.list(
                fixture.email(), otherChoirId, fixture.transitionId())
                .collectList().block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void serializesConcurrentDraftNumberAllocation() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<TransitionVersion> first = CompletableFuture.supplyAsync(
                    () -> createAfter(start, fixture), executor);
            CompletableFuture<TransitionVersion> second = CompletableFuture.supplyAsync(
                    () -> createAfter(start, fixture), executor);

            start.countDown();
            CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

            assertThat(first.join().getVersionNumber()).isNotEqualTo(
                    second.join().getVersionNumber());
            assertThat(jdbc.queryForList("""
                    SELECT version_number FROM transition_versions
                    WHERE transition_id = ?
                    ORDER BY version_number
                    """, Integer.class, fixture.transitionId()))
                    .containsExactly(1, 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsViolatedEvaluationWithoutAcceptableWarnings() {
        Fixture fixture = fixture();

        TransitionVersion draft = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Prueba de publicación")
                .block(TIMEOUT);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, fixture.choirId(), "Repertorio publicación " + UUID.randomUUID());

        long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, fixture.choirId());

        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'VIOLATED')
                """,
                draft.getId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                repertoireItemId);

        long evaluationId = jdbc.queryForObject("""
                SELECT id FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, Long.class, draft.getId());

        assertThatThrownBy(() -> service.publish(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                draft.getId(),
                evaluationId,
                java.util.List.of())
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("VIOLATED");

        assertThat(jdbc.queryForObject("""
                SELECT status FROM transition_versions WHERE id = ?
                """, String.class, draft.getId()))
                .isEqualTo("DRAFT");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_evidence
                WHERE transition_version_id = ?
                """, Integer.class, draft.getId()))
                .isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_decisions
                WHERE transition_version_id = ?
                """, Integer.class, draft.getId()))
                .isZero();
    }

     @Test
    void publishesSatisfiedEvaluationAndPersistsEvidenceWithoutDecisions() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Publicación de prueba")
                .block(TIMEOUT);

        String title = "Repertorio " + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, fixture.choirId(), title);

        long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, fixture.choirId(), title);

        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'SATISFIED')
                """,
                draft.getId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                repertoireItemId);

        long evaluationId = jdbc.queryForObject("""
                SELECT id FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Long.class, draft.getId());

        TransitionVersion published = service.publish(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                draft.getId(),
                evaluationId,
                java.util.List.of())
                .block(TIMEOUT);

        assertThat(published).isNotNull();
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedAt()).isNotNull();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_evidence
                WHERE transition_version_id = ?
                  AND evaluation_id = ?
                  AND transition_id = ?
                  AND from_formation_version_id = ?
                  AND to_formation_version_id = ?
                  AND separation_settings_revision = 1
                  AND global_separation_threshold = 0.0500000
                  AND published_by_email = ?
                  AND published_at IS NOT NULL
                """, Integer.class,
                draft.getId(),
                evaluationId,
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                fixture.email())).isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_decisions
                WHERE transition_version_id = ?
                  AND evaluation_id = ?
                """, Integer.class, draft.getId(), evaluationId)).isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_versions
                WHERE id = ? AND status = 'PUBLISHED'
                  AND published_at IS NOT NULL
                """, Integer.class, draft.getId())).isEqualTo(1);
    }

    @Test
    void rejectsRepeatedPublicationWithoutChangingHistoricalEvidence() {
        Fixture fixture = fixture();

        TransitionVersion draft = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "CHOIR-97 publicación repetida")
                .block(TIMEOUT);

        String suffix = UUID.randomUUID().toString();
        String repertoireTitle = "CHOIR-97 repeated publication " + suffix;
        jdbc.update("""
                INSERT INTO concerts (choir_id, title, status)
                VALUES (?, ?, 'PLANNED')
                """, fixture.choirId(), "Concierto " + suffix);
        Long concertId = jdbc.queryForObject(
                "SELECT id FROM concerts WHERE choir_id = ? AND title = ?",
                Long.class, fixture.choirId(), "Concierto " + suffix);

        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, fixture.choirId(), repertoireTitle);
        Long repertoireItemId = jdbc.queryForObject(
                "SELECT id FROM repertoire_items WHERE choir_id = ? AND title = ?",
                Long.class, fixture.choirId(), repertoireTitle);

        jdbc.update("""
                INSERT INTO concert_repertoire
                    (concert_id, repertoire_item_id, order_index)
                VALUES (?, ?, 0)
                """, concertId, repertoireItemId);

        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id,
                    transition_id,
                    from_formation_version_id,
                    to_formation_version_id,
                    repertoire_item_id,
                    separation_settings_revision,
                    global_separation_threshold,
                    status
                )
                VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'SATISFIED')
                """,
                draft.getId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                repertoireItemId);

        Long evaluationId = jdbc.queryForObject(
                """
                SELECT MAX(id) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """,
                Long.class, draft.getId());

        TransitionVersion published = service.publish(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                draft.getId(),
                evaluationId,
                java.util.List.of())
                .block(TIMEOUT);

        assertThat(published).isNotNull();
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");

        Long evidenceBefore = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM transition_publication_evidence
                WHERE transition_version_id = ? AND evaluation_id = ?
                """,
                Long.class, draft.getId(), evaluationId);

        Long decisionsBefore = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM transition_publication_decisions
                WHERE transition_version_id = ? AND evaluation_id = ?
                """,
                Long.class, draft.getId(), evaluationId);

        assertThat(evidenceBefore).isEqualTo(1L);
        assertThat(decisionsBefore).isZero();

        assertThatThrownBy(() -> service.publish(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                draft.getId(),
                evaluationId,
                java.util.List.of()).block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM transition_versions WHERE id = ?",
                String.class, draft.getId()))
                .isEqualTo("PUBLISHED");

        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM transition_publication_evidence
                WHERE transition_version_id = ? AND evaluation_id = ?
                """,
                Long.class, draft.getId(), evaluationId))
                .isEqualTo(evidenceBefore);

        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM transition_publication_decisions
                WHERE transition_version_id = ? AND evaluation_id = ?
                """,
                Long.class, draft.getId(), evaluationId))
                .isEqualTo(decisionsBefore);
    }


    private TransitionVersion createAfter(CountDownLatch start, Fixture fixture) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "No se liberó la barrera de creación concurrente");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrumpido al crear versiones concurrentemente", exception);
        }

        return service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Versión concurrente")
                .block(TIMEOUT);
    }

    private long foreignFormationVersion() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Director externo', ?)
                """, email);
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Coro externo " + suffix);
        long choirId = jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Coro externo " + suffix);
        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Formación externa " + suffix);
        long formationId = jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Formación externa " + suffix);
        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Externa')
                """, formationId);
        return jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private long createOtherChoir() {
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.test";
        jdbc.update("""
                INSERT INTO directors (display_name, email)
                VALUES ('Otro director', ?)
                """, email);
        long directorId = jdbc.queryForObject(
                "SELECT id FROM directors WHERE email = ?",
                Long.class, email);
        jdbc.update("""
                INSERT INTO choirs (director_id, name)
                VALUES (?, ?)
                """, directorId, "Otro coro " + suffix);
        return jdbc.queryForObject(
                "SELECT id FROM choirs WHERE director_id = ? AND name = ?",
                Long.class, directorId, "Otro coro " + suffix);
    }

    @Test
    void publishesViolatedEvaluationWithExplicitWarningAcceptance() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "Publicación con advertencia aceptada")
                .block(TIMEOUT);

        String title = "CHOIR-97 aceptación " + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, fixture.choirId(), title);
        Long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, fixture.choirId(), title);

        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, 'VIOLATED')
                """, draft.getId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                repertoireItemId);

        Long evaluationId = jdbc.queryForObject("""
                SELECT MAX(id) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Long.class, draft.getId());

        String explanation = "Excepción de separación documentada";
        jdbc.update("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, result_order_key, warning_order,
                    code, explanation, status
                ) VALUES (?, NULL, -1, 0, 'STAGE_SEPARATION', ?, 'VIOLATED')
                """, evaluationId, explanation);

        TransitionVersion published = service.publish(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                draft.getId(), evaluationId,
                java.util.List.of(
                        new com.rubengarcia.choir.transition.application.port.out
                                .TransitionVersionPublicationPort.WarningAcceptance(
                                        -1, 0, "Excepción aprobada por la dirección")))
                .block(TIMEOUT);

        assertThat(published).isNotNull();
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_decisions
                WHERE evaluation_id = ?
                  AND transition_version_id = ?
                  AND result_order = -1
                  AND warning_order = 0
                  AND warning_code = 'STAGE_SEPARATION'
                  AND warning_explanation = ?
                  AND decision = 'ACCEPTED'
                  AND director_email = ?
                  AND reason = 'Excepción aprobada por la dirección'
                """, Integer.class, evaluationId, draft.getId(),
                explanation, fixture.email())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_evidence
                WHERE transition_version_id = ? AND evaluation_id = ?
                """, Integer.class, draft.getId(), evaluationId)).isEqualTo(1);
    }

    @Test
    void rejectsNotEvaluableEvaluationWithoutWritingPublicationAudit() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "Evaluación no evaluable")
                .block(TIMEOUT);
        Long evaluationId = insertLifecycleEvaluation(fixture, draft, "NOT_EVALUABLE");

        assertThatThrownBy(() -> service.publish(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                draft.getId(), evaluationId, java.util.List.of()).block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no evaluable");

        assertPublicationAuditAbsent(draft.getId());
    }

    @Test
    void rejectsNotEvaluableWarningWithoutWritingPublicationAudit() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "Advertencia no evaluable")
                .block(TIMEOUT);
        Long evaluationId = insertLifecycleEvaluation(fixture, draft, "SATISFIED");

        jdbc.update("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, result_order_key, warning_order,
                    code, explanation, status
                ) VALUES (?, NULL, -1, 0, 'VISIBILITY_UNVERIFIED',
                          'No se puede comprobar la visibilidad', 'NOT_EVALUABLE')
                """, evaluationId);

        assertThatThrownBy(() -> service.publish(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                draft.getId(), evaluationId, java.util.List.of()).block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no evaluables");

        assertPublicationAuditAbsent(draft.getId());
    }

    @Test
    void rejectsNullWarningAcceptanceAsBadRequestWithoutWritingAudit() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "Aceptación nula")
                .block(TIMEOUT);
        Long evaluationId = insertLifecycleEvaluation(fixture, draft, "SATISFIED");

        assertThatThrownBy(() -> service.publish(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                draft.getId(), evaluationId,
                java.util.Arrays.asList(
                        (com.rubengarcia.choir.transition.application.port.out
                                .TransitionVersionPublicationPort.WarningAcceptance) null))
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cada aceptación");

        assertPublicationAuditAbsent(draft.getId());
    }

    @Test
    void rejectsVisibilityWarningAcceptanceWithoutWritingPublicationAudit() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "Visibilidad no aceptable")
                .block(TIMEOUT);
        Long evaluationId = insertLifecycleEvaluation(fixture, draft, "SATISFIED");

        jdbc.update("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, result_order_key, warning_order,
                    code, explanation, status
                ) VALUES (?, NULL, -1, 0, 'VISIBILITY_UNVERIFIED',
                          'No se puede comprobar la visibilidad', 'VIOLATED')
                """, evaluationId);

        assertThatThrownBy(() -> service.publish(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                draft.getId(), evaluationId,
                java.util.List.of(
                        new com.rubengarcia.choir.transition.application.port.out
                                .TransitionVersionPublicationPort.WarningAcceptance(
                                        -1, 0, "Aceptación que debe rechazarse")))
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertPublicationAuditAbsent(draft.getId());
    }

    @Test
    void rejectsDirectorVisibilityNotEvaluableWarningAcceptanceWithoutWritingPublicationAudit() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "CHOIR-94 visibilidad no evaluable")
                .block(TIMEOUT);
        Long evaluationId = insertLifecycleEvaluation(fixture, draft, "VIOLATED");

        jdbc.update("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, result_order_key, warning_order,
                    code, explanation, status
                ) VALUES (?, NULL, -1, 0, 'DIRECTOR_VISIBILITY_NOT_EVALUABLE',
                          'No se puede comprobar la visibilidad del director', 'VIOLATED')
                """, evaluationId);

        assertThatThrownBy(() -> service.publish(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                draft.getId(), evaluationId,
                java.util.List.of(
                        new com.rubengarcia.choir.transition.application.port.out
                                .TransitionVersionPublicationPort.WarningAcceptance(
                                        -1, 0, "No debe aceptarse la visibilidad no verificable")))
                .block(TIMEOUT))
                .isInstanceOf(ResponseStatusException.class);

        assertPublicationAuditAbsent(draft.getId());
    }

    @Test
    void rollsBackPublicationDecisionsWhenEvidencePersistenceFails() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(), fixture.choirId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                "Prueba de rollback")
                .block(TIMEOUT);
        Long evaluationId = insertLifecycleEvaluation(fixture, draft, "VIOLATED");

        String explanation = "Advertencia para probar rollback";
        jdbc.update("""
                INSERT INTO transition_rule_evaluation_warnings (
                    evaluation_id, result_order, result_order_key, warning_order,
                    code, explanation, status
                ) VALUES (?, NULL, -1, 0, 'STAGE_SEPARATION', ?, 'VIOLATED')
                """, evaluationId, explanation);

        jdbc.execute("""
                ALTER TABLE transition_publication_evidence
                ADD CONSTRAINT ck_test_fail_publication_evidence
                CHECK (transition_version_id <> %d OR global_separation_threshold < 0)
                """.formatted(draft.getId()));

        try {
            assertThatThrownBy(() -> service.publish(
                    fixture.email(), fixture.choirId(), fixture.transitionId(),
                    draft.getId(), evaluationId,
                    java.util.List.of(
                            new com.rubengarcia.choir.transition.application.port.out
                                    .TransitionVersionPublicationPort.WarningAcceptance(
                                            -1, 0, "Aceptación para probar rollback")))
                    .block(TIMEOUT))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            assertPublicationAuditAbsent(draft.getId());
        } finally {
            jdbc.execute("""
                    ALTER TABLE transition_publication_evidence
                    DROP CONSTRAINT ck_test_fail_publication_evidence
                    """);
        }
    }


    @Test
    void publicationEvidenceMustMatchItsTransitionVersionSnapshot() {
        Fixture fixture = fixture();
        TransitionVersion draft = service.createDraft(
                fixture.email(),
                fixture.choirId(),
                fixture.transitionId(),
                fixture.fromFormationVersionId(),
                fixture.toFormationVersionId(),
                "Evidencia con referencias exactas")
                .block(TIMEOUT);
        long evaluationId = insertLifecycleEvaluation(
                fixture, draft, "SATISFIED");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transition_publication_evidence (
                    transition_version_id, evaluation_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    separation_settings_revision, global_separation_threshold,
                    published_by_email
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, ?)
                """,
                draft.getId(),
                evaluationId,
                fixture.transitionId(),
                fixture.toFormationVersionId(),
                fixture.fromFormationVersionId(),
                fixture.email()))
                .isInstanceOf(DataAccessException.class);
    }

    private Long insertLifecycleEvaluation(
            Fixture fixture, TransitionVersion draft, String status) {
        String title = "CHOIR-97 evaluación " + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO repertoire_items (choir_id, title)
                VALUES (?, ?)
                """, fixture.choirId(), title);
        Long repertoireItemId = jdbc.queryForObject("""
                SELECT id FROM repertoire_items
                WHERE choir_id = ? AND title = ?
                """, Long.class, fixture.choirId(), title);

        jdbc.update("""
                INSERT INTO transition_rule_evaluations (
                    transition_version_id, transition_id,
                    from_formation_version_id, to_formation_version_id,
                    repertoire_item_id, separation_settings_revision,
                    global_separation_threshold, status
                ) VALUES (?, ?, ?, ?, ?, 1, 0.0500000, ?)
                """, draft.getId(), fixture.transitionId(),
                fixture.fromFormationVersionId(), fixture.toFormationVersionId(),
                repertoireItemId, status);

        return jdbc.queryForObject("""
                SELECT MAX(id) FROM transition_rule_evaluations
                WHERE transition_version_id = ?
                """, Long.class, draft.getId());
    }

    private void assertPublicationAuditAbsent(long versionId) {
        assertThat(jdbc.queryForObject("""
                SELECT status FROM transition_versions WHERE id = ?
                """, String.class, versionId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_evidence
                WHERE transition_version_id = ?
                """, Integer.class, versionId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transition_publication_decisions
                WHERE transition_version_id = ?
                """, Integer.class, versionId)).isZero();
    }

    private Fixture fixture() {
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

        long fromFormationId = createFormation(choirId, "FROM " + suffix);
        long toFormationId = createFormation(choirId, "TO " + suffix);
        long fromVersionId = createFormationVersion(fromFormationId);
        long toVersionId = createFormationVersion(toFormationId);

        jdbc.update("""
                INSERT INTO transitions (choir_id, name)
                VALUES (?, ?)
                """, choirId, "Transición " + suffix);
        long transitionId = jdbc.queryForObject(
                "SELECT id FROM transitions WHERE choir_id = ? AND name = ?",
                Long.class, choirId, "Transición " + suffix);

        return new Fixture(
                email, choirId, transitionId, fromVersionId, toVersionId);
    }

    private long createFormation(long choirId, String name) {
        jdbc.update("""
                INSERT INTO formations (choir_id, name)
                VALUES (?, ?)
                """, choirId, name);
        return jdbc.queryForObject(
                "SELECT id FROM formations WHERE choir_id = ? AND name = ?",
                Long.class, choirId, name);
    }

    private long createFormationVersion(long formationId) {
        jdbc.update("""
                INSERT INTO formation_versions
                    (formation_id, version_number, status, notes)
                VALUES (?, 1, 'PUBLISHED', 'Versión de prueba')
                """, formationId);
        return jdbc.queryForObject("""
                SELECT id FROM formation_versions
                WHERE formation_id = ? AND version_number = 1
                """, Long.class, formationId);
    }

    private record Fixture(
            String email,
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId) {
    }
}
