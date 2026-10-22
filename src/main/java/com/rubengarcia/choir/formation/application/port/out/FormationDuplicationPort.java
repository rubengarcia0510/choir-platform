package com.rubengarcia.choir.formation.application.port.out;

import reactor.core.publisher.Mono;

public interface FormationDuplicationPort {

    Mono<FormationCatalogPort.CreatedFormation> duplicate(
            long choirId,
            long concertId,
            long repertoireItemId,
            long sourceFormationId,
            Long sourceVersionId,
            String name,
            String description);
}
