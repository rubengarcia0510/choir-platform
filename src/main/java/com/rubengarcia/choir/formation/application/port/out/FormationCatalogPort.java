package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationCatalogPort {

    Flux<Formation> findForOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId);

    Mono<CreatedFormation> createWithInitialDraft(
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description);

    record CreatedFormation(
            Formation formation,
            FormationVersion draftVersion) {
    }
}
