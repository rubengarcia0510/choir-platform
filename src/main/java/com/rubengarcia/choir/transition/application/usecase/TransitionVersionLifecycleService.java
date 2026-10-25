package com.rubengarcia.choir.transition.application.usecase;

import com.rubengarcia.choir.domain.TransitionVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransitionVersionLifecycleService {

    Flux<TransitionVersion> list(
            String email, long choirId, long transitionId);

    Mono<TransitionVersion> createDraft(
            String email,
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            String instructions);
}
