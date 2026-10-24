package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import reactor.core.publisher.Mono;

public interface FormationDuplicationPort {

    Mono<CreatedFormation> duplicate(
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
