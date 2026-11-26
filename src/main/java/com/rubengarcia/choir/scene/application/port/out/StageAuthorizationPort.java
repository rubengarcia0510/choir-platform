package com.rubengarcia.choir.scene.application.port.out;

import reactor.core.publisher.Mono;

/** Autorización del director. Delega en los mecanismos existentes del proyecto; no duplica reglas. */
public interface StageAuthorizationPort {

    Mono<Void> requireOwnedChoir(String directorEmail, long choirId);

    Mono<Void> requireOwnedOccurrence(
            String directorEmail, long choirId, long concertId, long repertoireItemId);
}
