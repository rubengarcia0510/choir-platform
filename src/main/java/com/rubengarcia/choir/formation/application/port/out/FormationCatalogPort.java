package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.math.BigDecimal;
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

    Mono<DraftDetails> replaceDraftPositions(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long versionId,
            List<PositionUpdate> positions);

    record PositionUpdate(
            Long singerId,
            String positionLabel,
            BigDecimal x,
            BigDecimal y) {
    }

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
