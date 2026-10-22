package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import reactor.core.publisher.Mono;

public interface FormationDuplicationUseCase {

    Mono<FormationCatalogPort.CreatedFormation> duplicate(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long sourceFormationId,
            Long sourceVersionId,
            String name,
            String description);
}
