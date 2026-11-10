package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import reactor.core.publisher.Mono;

public interface ConcertOptimizationPreferencesService {

    Mono<ConcertOptimizationPreferences> get(
            String email, long choirId, long concertId);

    Mono<ConcertOptimizationPreferences> update(
            String email,
            long choirId,
            long concertId,
            ConcertOptimizationPreferences preferences);
}
