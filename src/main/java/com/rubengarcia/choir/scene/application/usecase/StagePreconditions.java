package com.rubengarcia.choir.scene.application.usecase;

import reactor.core.publisher.Mono;

/**
 * Validación de entrada de los casos de uso de escenario. Se ejecuta antes de la autorización: una
 * entrada inválida nunca llega a los puertos.
 */
final class StagePreconditions {

    private StagePreconditions() {
    }

    static Mono<Void> requirePositive(String directorEmail, long... identifiers) {
        if (directorEmail == null || directorEmail.isBlank()) {
            return Mono.error(new IllegalArgumentException("El director es obligatorio"));
        }
        for (long identifier : identifiers) {
            if (identifier <= 0) {
                return Mono.error(new IllegalArgumentException("Los identificadores deben ser positivos"));
            }
        }
        return Mono.empty();
    }
}
