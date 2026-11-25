package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.in.FormationVisibilityConstraintUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVisibilityConstraintPort;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Caso de uso de restricciones de visibilidad. Autoriza al director sobre la ocurrencia antes de
 * delegar en el puerto de persistencia. No contiene SQL ni reglas geométricas.
 */
@Service
public class FormationVisibilityConstraintService
        implements FormationVisibilityConstraintUseCase {

    private final FormationVisibilityConstraintPort constraintPort;
    private final FormationOccurrenceAuthorizationPort occurrenceAuthorization;
    private final FormationOccurrenceContextAuthorizer contextAuthorizer;

    public FormationVisibilityConstraintService(
            FormationVisibilityConstraintPort constraintPort,
            FormationOccurrenceAuthorizationPort occurrenceAuthorization,
            FormationOccurrenceContextAuthorizer contextAuthorizer) {
        this.constraintPort = constraintPort;
        this.occurrenceAuthorization = occurrenceAuthorization;
        this.contextAuthorizer = contextAuthorizer;
    }

    @Override
    public Flux<FormationVisibilityConstraint> list(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return contextAuthorizer
                .authorize(directorEmail, choirId, concertId, repertoireItemId, formationId)
                .thenMany(Flux.defer(() -> constraintPort.findForFormation(
                        choirId, concertId, repertoireItemId, formationId)));
    }

    @Override
    public Mono<FormationVisibilityConstraint> create(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long protectedSingerId) {
        return occurrenceAuthorization
                .requireOwnedOccurrence(directorEmail, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> constraintPort.create(
                        choirId, concertId, repertoireItemId, formationId, protectedSingerId)));
    }

    @Override
    public Mono<FormationVisibilityConstraint> update(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId,
            boolean active) {
        return occurrenceAuthorization
                .requireOwnedOccurrence(directorEmail, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> constraintPort.update(
                        choirId, concertId, repertoireItemId, formationId, constraintId, active)));
    }

    @Override
    public Mono<Boolean> delete(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long constraintId) {
        return occurrenceAuthorization
                .requireOwnedOccurrence(directorEmail, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> constraintPort.delete(
                        choirId, concertId, repertoireItemId, formationId, constraintId)));
    }
}
