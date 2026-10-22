package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import reactor.core.publisher.Mono;

public interface FormationDuplicationUseCase {

    Mono<CreatedFormation> duplicate(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long sourceFormationId,
            Long sourceVersionId,
            String name,
            String description);

    record CreatedFormation(
            Formation formation,
            FormationVersion draftVersion) {
    }
}
