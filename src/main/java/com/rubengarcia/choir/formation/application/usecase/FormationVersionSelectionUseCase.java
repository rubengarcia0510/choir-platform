package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import com.rubengarcia.choir.formation.domain.FormationVersionSelectionHistory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionSelectionUseCase {

    Mono<FormationVersionSelection> findSelection(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId);

    Mono<FormationVersionSelection> selectPublishedVersion(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long formationVersionId);

    Flux<FormationVersionSelectionHistory> findSelectionHistory(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId);
}
