package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.exception.FormationVersionNotFoundException;
import com.rubengarcia.choir.formation.application.port.in.FormationVisibilityEvaluationUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityRosterPort;
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
 * Evalúa restricciones de visibilidad activas contra el borrador (DRAFT), con el roster
 * de coreutas activos asignados en concert_vocal_assignments. Funciona con conciertos
 * publicados y no publicados. Solo lee; no modifica datos.
 */
@Service
public class FormationVisibilityEvaluationService
        implements FormationVisibilityEvaluationUseCase {

    private final FormationVisibilityConstraintPort constraintPort;
    private final FormationCatalogPort catalog;
    private final FormationVisibilityRosterPort rosterPort;
    private final FormationOccurrenceContextAuthorizer authorizer;

    public FormationVisibilityEvaluationService(
            FormationVisibilityConstraintPort constraintPort,
            FormationCatalogPort catalog,
            FormationVisibilityRosterPort rosterPort,
            FormationOccurrenceContextAuthorizer authorizer) {
        this.constraintPort = constraintPort;
        this.catalog = catalog;
        this.rosterPort = rosterPort;
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
                        readRoster(choirId, concertId, repertoireItemId))))
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

    /**
     * Posiciones del DRAFT de esta formación. Si no hay DRAFT, propaga
     * FormationVersionNotFoundException: no se devuelve una evaluación con posiciones vacías.
     */
    private Mono<List<FormationPosition>> readDraftPositions(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return catalog
                .findDraftForOccurrence(choirId, concertId, repertoireItemId, formationId)
                .map(FormationCatalogPort.DraftDetails::positions)
                .switchIfEmpty(Mono.error(new FormationVersionNotFoundException()));
    }

    private Mono<Set<Long>> readRoster(long choirId, long concertId, long repertoireItemId) {
        return rosterPort
                .findActiveAssignedSingerIds(choirId, concertId, repertoireItemId)
                .collect(Collectors.toSet());
    }
}
