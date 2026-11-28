package com.rubengarcia.choir.scene.application.port.out;

import reactor.core.publisher.Mono;

/**
 * Autorización del director sobre escenarios.
 *
 * <p>Errores: {@code ResponseStatusException} 401 si el director no está identificado; 404 si el recurso
 * no existe en el coro indicado; {@code StageAccessDeniedException} (403) si el recurso existe y pertenece
 * a otro director.</p>
 */
public interface StageAuthorizationPort {

    Mono<Void> requireOwnedChoir(String directorEmail, long choirId);

    Mono<Void> requireOwnedOccurrence(
            String directorEmail, long choirId, long concertId, long repertoireItemId);
}
