package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.exception.FormationNotFoundException;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Autoriza un contexto completo de formación: el director debe ser dueño de la
 * ocurrencia (coro, concierto y elemento de repertorio) y la formación debe
 * pertenecer a esa ocurrencia. Ser dueño de la ocurrencia no implica por sí
 * solo que la formación le pertenezca.
 */
@Component
public class FormationOccurrenceContextAuthorizer {

    private final FormationOccurrenceAuthorizationPort occurrenceAuthorization;
    private final FormationCatalogPort catalog;

    public FormationOccurrenceContextAuthorizer(
            FormationOccurrenceAuthorizationPort occurrenceAuthorization,
            FormationCatalogPort catalog) {
        this.occurrenceAuthorization = occurrenceAuthorization;
        this.catalog = catalog;
    }

    public Mono<Void> authorize(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return occurrenceAuthorization
                .requireOwnedOccurrence(email, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> requireFormationInOccurrence(
                        choirId, concertId, repertoireItemId, formationId)));
    }

    private Mono<Void> requireFormationInOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {
        return catalog.findForOccurrence(choirId, concertId, repertoireItemId)
                .filter(formation -> formation.getId().equals(formationId))
                .hasElements()
                .flatMap(found -> found
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new FormationNotFoundException()));
    }
}
