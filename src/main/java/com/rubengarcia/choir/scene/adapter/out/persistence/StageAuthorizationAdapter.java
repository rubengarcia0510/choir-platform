package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Autorización de escenarios delegada en los mecanismos existentes; no duplica reglas de seguridad. */
@Component
public class StageAuthorizationAdapter implements StageAuthorizationPort {

    private final ResourceAuthorizationService resourceAuthorizationService;

    public StageAuthorizationAdapter(ResourceAuthorizationService resourceAuthorizationService) {
        this.resourceAuthorizationService = resourceAuthorizationService;
    }

    @Override
    public Mono<Void> requireOwnedChoir(String directorEmail, long choirId) {
        return resourceAuthorizationService.requireOwnedChoir(directorEmail, choirId);
    }

    @Override
    public Mono<Void> requireOwnedOccurrence(
            String directorEmail, long choirId, long concertId, long repertoireItemId) {
        return resourceAuthorizationService.requireOwnedConcertWork(
                directorEmail, choirId, concertId, repertoireItemId);
    }
}
