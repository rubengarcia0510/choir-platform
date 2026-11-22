package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.in.FormationMobilityEvaluationUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationMobilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.MobilityEvaluation;
import com.rubengarcia.choir.formation.domain.MobilityEvaluator;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Evalúa restricciones de movilidad activas contra las posiciones del borrador.
 * Solo lee: no modifica restricciones, posiciones ni snapshots.
 */
@Service
public class FormationMobilityEvaluationService
        implements FormationMobilityEvaluationUseCase {

    private final FormationMobilityConstraintPort constraintPort;
    private final FormationCatalogPort catalog;
    private final FormationOccurrenceContextAuthorizer authorizer;

    public FormationMobilityEvaluationService(
            FormationMobilityConstraintPort constraintPort,
            FormationCatalogPort catalog,
            FormationOccurrenceContextAuthorizer authorizer) {
        this.constraintPort = constraintPort;
        this.catalog = catalog;
        this.authorizer = authorizer;
    }

    @Override
    public Mono<MobilityEvaluation> evaluate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return Mono.defer(() -> authorizer
                .authorize(email, choirId, concertId, repertoireItemId, formationId)
                .then(Mono.defer(() -> Mono.zip(
                        readConstraints(choirId, concertId, repertoireItemId, formationId),
                        readDraftPositions(choirId, concertId, repertoireItemId, formationId))))
                .map(data -> MobilityEvaluator.evaluate(data.getT1(), data.getT2())));
    }

    private Mono<List<FormationMobilityConstraint>> readConstraints(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return constraintPort
                .findForFormation(choirId, concertId, repertoireItemId, formationId)
                .collectList();
    }

    /**
     * Posiciones del borrador de esta formación. Sin borrador, la lista es
     * vacía y cada restricción activa queda como no evaluable.
     */
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
}
