package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionLifecycleAuthorizationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationVersionLifecyclePort;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationVersionLifecycleUseCaseImpl
        implements FormationVersionLifecycleUseCase {

    private final FormationVersionLifecyclePort lifecyclePort;
    private final FormationVersionLifecycleAuthorizationPort authorizationPort;

    public FormationVersionLifecycleUseCaseImpl(
            FormationVersionLifecyclePort lifecyclePort,
            FormationVersionLifecycleAuthorizationPort authorizationPort) {
        this.lifecyclePort = lifecyclePort;
        this.authorizationPort = authorizationPort;
    }

    @Override
    public Flux<FormationVersion> list(
            String email, long choirId, long formationId) {
        return authorizationPort.requireOwnedChoir(email, choirId)
                .thenMany(Flux.defer(
                        () -> lifecyclePort.list(choirId, formationId)));
    }

    @Override
    public Mono<FormationVersion> createDraft(
            String email, long choirId, long formationId, String notes) {
        return authorizationPort.requireOwnedChoir(email, choirId)
                .then(Mono.defer(
                        () -> lifecyclePort.createDraft(
                                choirId, formationId, notes)));
    }

    @Override
    public Mono<FormationVersion> published(
            String email, long choirId, long formationId) {
        return authorizationPort.requireOwnedChoir(email, choirId)
                .then(Mono.defer(
                        () -> lifecyclePort.published(choirId, formationId)));
    }

    @Override
    public Mono<FormationVersion> publish(
            String email, long choirId, long formationId, long versionId) {
        return authorizationPort.requireOwnedChoir(email, choirId)
                .then(Mono.defer(
                        () -> lifecyclePort.publish(
                                choirId, formationId, versionId)));
    }
}
