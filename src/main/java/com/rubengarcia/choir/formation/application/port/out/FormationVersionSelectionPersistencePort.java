package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import com.rubengarcia.choir.formation.domain.FormationVersionSelectionHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionSelectionPersistencePort {

    Mono<FormationVersionSelection> findSelection(
            long concertId,
            long repertoireItemId);

    Mono<FormationVersionSelection> selectPublishedVersion(
            long concertId,
            long repertoireItemId,
            long formationId,
            long formationVersionId,
            String actorEmail);

    Flux<FormationVersionSelectionHistory> findSelectionHistory(
            long concertId,
            long repertoireItemId);
}
