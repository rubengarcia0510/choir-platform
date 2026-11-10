package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.service.ResourceAuthorizationService;
import com.rubengarcia.choir.transition.application.port.out.ConcertOptimizationPreferencesPort;
import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class ConcertOptimizationPreferencesServiceImpl
        implements ConcertOptimizationPreferencesService {

    private final ConcertOptimizationPreferencesPort preferencesPort;
    private final ResourceAuthorizationService authorizationService;

    public ConcertOptimizationPreferencesServiceImpl(
            ConcertOptimizationPreferencesPort preferencesPort,
            ResourceAuthorizationService authorizationService) {
        this.preferencesPort = preferencesPort;
        this.authorizationService = authorizationService;
    }

    @Override
    public Mono<ConcertOptimizationPreferences> get(
            String email, long choirId, long concertId) {
        return authorizationService.requireOwnedConcert(
                        email, choirId, concertId)
                .then(Mono.defer(() -> preferencesPort.findByConcertId(concertId)))
                .defaultIfEmpty(ConcertOptimizationPreferences.defaults());
    }

    @Override
    public Mono<ConcertOptimizationPreferences> update(
            String email,
            long choirId,
            long concertId,
            ConcertOptimizationPreferences preferences) {
        return authorizationService.requireOwnedConcert(
                        email, choirId, concertId)
                .then(Mono.defer(() -> {
                    if (preferences == null) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Las preferencias de optimización son obligatorias"));
                    }
                    return preferencesPort.save(concertId, preferences);
                }));
    }
}
