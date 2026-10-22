package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationPosition;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import java.math.BigDecimal;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationDraftManagementUseCase {

    Flux<Formation> list(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId);

    Mono<CreatedFormation> create(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description);

    Mono<DraftDetails> getDraft(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId);

    Mono<DraftDetails> replaceDraftPositions(
            String directorEmail,
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
