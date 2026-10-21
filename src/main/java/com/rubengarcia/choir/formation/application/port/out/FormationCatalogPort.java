package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.util.List;
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

    Mono<DraftDetails> findDraftForOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    record CreatedFormation(
            Formation formation,
            FormationVersion draftVersion) {
    }

    record DraftDetails(
            Formation formation,
            FormationVersion draftVersion,
            List<FormationPosition> positions) {
    }
}
