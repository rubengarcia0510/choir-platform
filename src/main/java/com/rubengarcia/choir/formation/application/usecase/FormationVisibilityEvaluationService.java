package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.model.VocalAssignment;
import com.rubengarcia.choir.formation.application.port.in.FormationVisibilityEvaluationUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationPlanningPersistencePort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluation;
import com.rubengarcia.choir.formation.domain.VisibilityEvaluator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Evalúa restricciones de visibilidad activas contra el borrador, con el roster de
 * coreutas asignados a la obra en el concierto. Solo lee; no modifica nada.
 *
 * <p>El roster proviene de las asignaciones vocales publicadas, la misma fuente que
 * usa la planificación. Un concierto no publicado no tiene asignaciones publicadas:
 * sus posiciones quedan como no evaluables.</p>
 */
@Service
public class FormationVisibilityEvaluationService
        implements FormationVisibilityEvaluationUseCase {

    private final FormationVisibilityConstraintPort constraintPort;
    private final FormationCatalogPort catalog;
    private final FormationPlanningPersistencePort planning;
    private final FormationOccurrenceContextAuthorizer authorizer;

    public FormationVisibilityEvaluationService(
            FormationVisibilityConstraintPort constraintPort,
            FormationCatalogPort catalog,
            FormationPlanningPersistencePort planning,
            FormationOccurrenceContextAuthorizer authorizer) {
        this.constraintPort = constraintPort;
        this.catalog = catalog;
        this.planning = planning;
        this.authorizer = authorizer;
    }

    @Override
    public Mono<VisibilityEvaluation> evaluate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return Mono.defer(() -> authorizer
                .authorize(email, choirId, concertId, repertoireItemId, formationId)
                .then(Mono.defer(() -> Mono.zip(
                        readConstraints(choirId, concertId, repertoireItemId, formationId),
                        readDraftPositions(choirId, concertId, repertoireItemId, formationId),
                        readRoster(concertId, repertoireItemId))))
                .map(data -> VisibilityEvaluator.evaluate(
                        data.getT1(), data.getT2(), data.getT3())));
    }

    private Mono<List<FormationVisibilityConstraint>> readConstraints(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return constraintPort
                .findForFormation(choirId, concertId, repertoireItemId, formationId)
                .collectList();
    }

    /** Posiciones del borrador de esta formación; sin borrador, lista vacía. */
    private Mono<List<FormationPosition>> readDraftPositions(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return catalog
                .findDraftForOccurrence(choirId, concertId, repertoireItemId, formationId)
                .map(FormationCatalogPort.DraftDetails::positions)
                .defaultIfEmpty(List.of());
    }

    private Mono<Set<Long>> readRoster(long concertId, long repertoireItemId) {
        return planning
                .vocalAssignmentsForPlanning(concertId, repertoireItemId)
                .filter(assignment -> assignment.singerId() != null)
                .map(VocalAssignment::singerId)
                .collect(Collectors.toSet());
    }
}
