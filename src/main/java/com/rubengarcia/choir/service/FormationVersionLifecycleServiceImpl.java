package com.rubengarcia.choir.service;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionLifecyclePort;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationVersionLifecycleServiceImpl
        implements FormationVersionLifecycleService {

    private final FormationVersionLifecyclePort lifecyclePort;
    private final ResourceAuthorizationService authorizationService;

    public FormationVersionLifecycleServiceImpl(
            FormationVersionLifecyclePort lifecyclePort,
            ResourceAuthorizationService authorizationService) {
        this.lifecyclePort = lifecyclePort;
        this.authorizationService = authorizationService;
    }

    @Override
    public Flux<FormationVersion> list(
            String email, long choirId, long formationId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .thenMany(lifecyclePort.list(choirId, formationId));
    }

    @Override
    public Mono<FormationVersion> createDraft(
            String email, long choirId, long formationId, String notes) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(lifecyclePort.createDraft(choirId, formationId, notes));
    }

    @Override
    public Mono<FormationVersion> published(
            String email, long choirId, long formationId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(lifecyclePort.published(choirId, formationId));
    }

    @Override
    public Mono<FormationVersion> publish(
            String email, long choirId, long formationId, long versionId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(lifecyclePort.publish(choirId, formationId, versionId));
    }
}
