package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionLifecyclePort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TransitionVersionLifecycleServiceImpl
        implements TransitionVersionLifecycleService {

    private final TransitionVersionLifecyclePort lifecyclePort;
    private final ResourceAuthorizationService authorizationService;

    public TransitionVersionLifecycleServiceImpl(
            TransitionVersionLifecyclePort lifecyclePort,
            ResourceAuthorizationService authorizationService) {
        this.lifecyclePort = lifecyclePort;
        this.authorizationService = authorizationService;
    }

    @Override
    public Flux<TransitionVersion> list(
            String email, long choirId, long transitionId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .thenMany(lifecyclePort.list(choirId, transitionId));
    }

    @Override
    public Mono<TransitionVersion> createDraft(
            String email,
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            String instructions) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(lifecyclePort.createDraft(
                        choirId,
                        transitionId,
                        fromFormationVersionId,
                        toFormationVersionId,
                        instructions));
    }
}
