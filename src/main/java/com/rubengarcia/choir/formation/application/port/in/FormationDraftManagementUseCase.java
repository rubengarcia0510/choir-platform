package com.rubengarcia.choir.formation.application.port.in;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.domain.Formation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationDraftManagementUseCase {

    Flux<Formation> list(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId);

    Mono<FormationCatalogPort.CreatedFormation> create(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description);
}
