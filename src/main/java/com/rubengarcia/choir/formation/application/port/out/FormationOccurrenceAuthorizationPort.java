package com.rubengarcia.choir.formation.application.port.out;

import reactor.core.publisher.Mono;

public interface FormationOccurrenceAuthorizationPort {

    Mono<Void> requireOwnedOccurrence(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId);
}
