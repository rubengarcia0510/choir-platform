package com.rubengarcia.choir.formation.adapter.out.authorization;

import com.rubengarcia.choir.formation.application.port.out.FormationPlanningResourceAuthorizationPort;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FormationPlanningResourceAuthorizationAdapter
        implements FormationPlanningResourceAuthorizationPort {

    private final ResourceAuthorizationService resourceAuthorizationService;

    public FormationPlanningResourceAuthorizationAdapter(
            ResourceAuthorizationService resourceAuthorizationService) {
        this.resourceAuthorizationService = resourceAuthorizationService;
    }

    @Override
    public Mono<Void> requireOwnedFormationVersion(
            String email, long choirId, long formationVersionId) {
        return resourceAuthorizationService.requireOwnedFormationVersion(
                email, choirId, formationVersionId);
    }

    @Override
    public Mono<Void> requireOwnedConcertWork(
            String email, long choirId, long concertId, long repertoireItemId) {
        return resourceAuthorizationService.requireOwnedConcertWork(
                email, choirId, concertId, repertoireItemId);
    }

    @Override
    public Mono<Void> requireOwnedTransitionVersion(
            String email, long choirId, long transitionVersionId) {
        return resourceAuthorizationService.requireOwnedTransitionVersion(
                email, choirId, transitionVersionId);
    }

    @Override
    public Mono<Void> requireOwnedPosition(
            String email, long choirId, long positionId) {
        return resourceAuthorizationService.requireOwnedPosition(
                email, choirId, positionId);
    }
}
