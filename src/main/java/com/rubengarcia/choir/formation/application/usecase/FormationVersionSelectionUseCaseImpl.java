package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationPlanningResourceAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVersionSelectionPersistencePort;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import com.rubengarcia.choir.formation.domain.FormationVersionSelectionHistory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationVersionSelectionUseCaseImpl
        implements FormationVersionSelectionUseCase {

    private final FormationVersionSelectionPersistencePort persistencePort;
    private final FormationPlanningResourceAuthorizationPort authorizationPort;

    public FormationVersionSelectionUseCaseImpl(
            FormationVersionSelectionPersistencePort persistencePort,
            FormationPlanningResourceAuthorizationPort authorizationPort) {
        this.persistencePort = persistencePort;
        this.authorizationPort = authorizationPort;
    }

    @Override
    public Mono<FormationVersionSelection> findSelection(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId) {
        return authorizationPort.requireOwnedConcertWork(
                        email, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() ->
                        persistencePort.findSelection(concertId, repertoireItemId)));
    }

    @Override
    public Mono<FormationVersionSelection> selectPublishedVersion(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long formationVersionId) {
        return authorizationPort.requireOwnedConcertWork(
                        email, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> persistencePort.selectPublishedVersion(
                        concertId,
                        repertoireItemId,
                        formationId,
                        formationVersionId,
                        email)));
    }

    @Override
    public Flux<FormationVersionSelectionHistory> findSelectionHistory(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId) {
        return authorizationPort.requireOwnedConcertWork(
                        email, choirId, concertId, repertoireItemId)
                .thenMany(Flux.defer(() ->
                        persistencePort.findSelectionHistory(
                                concertId, repertoireItemId)));
    }
}
