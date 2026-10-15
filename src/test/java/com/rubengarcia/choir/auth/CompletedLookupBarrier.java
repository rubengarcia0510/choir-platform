package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Barrera reutilizable para pruebas de consultas concurrentes.
 * Espera a que terminen las consultas reales antes de devolver sus resultados.
 */
final class CompletedLookupBarrier {

    private final int requiredCompletions;
    private final Duration timeout;
    private final Sinks.One<Void> barrier = Sinks.one();
    private final AtomicInteger completedLookups = new AtomicInteger();

    CompletedLookupBarrier(int requiredCompletions, Duration timeout) {
        if (requiredCompletions < 2) {
            throw new IllegalArgumentException(
                    "Se requieren al menos dos consultas concurrentes");
        }
        this.requiredCompletions = requiredCompletions;
        this.timeout = timeout;
    }

    Mono<Director> await(Mono<Director> actualQuery) {
        return actualQuery
                .singleOptional()
                .flatMap(result -> {
                    int completed = completedLookups.incrementAndGet();

                    if (completed == requiredCompletions) {
                        barrier.tryEmitEmpty();
                    }

                    return barrier.asMono()
                            .timeout(timeout)
                            .thenReturn(result);
                })
                .flatMap(this::restoreResult);
    }

    private Mono<Director> restoreResult(Optional<Director> result) {
        return result.map(Mono::just).orElseGet(Mono::empty);
    }

    int completedLookups() {
        return completedLookups.get();
    }

    void release() {
        barrier.tryEmitEmpty();
    }
}
