package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionLifecyclePort;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionPublicationPort;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionPublicationPort.WarningAcceptance;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class TransitionVersionLifecycleServiceImpl
        implements TransitionVersionLifecycleService {

    private final TransitionVersionLifecyclePort lifecyclePort;
    private final TransitionVersionPublicationPort publicationPort;
    private final ResourceAuthorizationService authorizationService;

    public TransitionVersionLifecycleServiceImpl(
            TransitionVersionLifecyclePort lifecyclePort,
            TransitionVersionPublicationPort publicationPort,
            ResourceAuthorizationService authorizationService) {
        this.lifecyclePort = lifecyclePort;
        this.publicationPort = publicationPort;
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

    @Override
    public Mono<TransitionVersion> publish(
            String email,
            long choirId,
            long transitionId,
            long transitionVersionId,
            long evaluationId,
            List<WarningAcceptance> acceptedWarnings) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(Mono.defer(() -> publicationPort.publish(
                        new TransitionVersionPublicationPort.PublicationCommand(
                                choirId,
                                transitionId,
                                transitionVersionId,
                                evaluationId,
                                email,
                                acceptedWarnings))));
    }
}
