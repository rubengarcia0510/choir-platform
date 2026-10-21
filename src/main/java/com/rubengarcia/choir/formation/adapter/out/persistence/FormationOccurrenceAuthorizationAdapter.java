package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FormationOccurrenceAuthorizationAdapter
        implements FormationOccurrenceAuthorizationPort {

    private final ResourceAuthorizationService resourceAuthorizationService;

    public FormationOccurrenceAuthorizationAdapter(
            ResourceAuthorizationService resourceAuthorizationService) {
        this.resourceAuthorizationService = resourceAuthorizationService;
    }

    @Override
    public Mono<Void> requireOwnedOccurrence(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId) {

        return resourceAuthorizationService.requireOwnedConcertWork(
                directorEmail,
                choirId,
                concertId,
                repertoireItemId);
    }
}
