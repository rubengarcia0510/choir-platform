package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import reactor.core.publisher.Mono;

public interface ConcertOptimizationPreferencesPort {

    Mono<ConcertOptimizationPreferences> findByConcertId(long concertId);

    Mono<ConcertOptimizationPreferences> save(
            long concertId,
            ConcertOptimizationPreferences preferences);
}
