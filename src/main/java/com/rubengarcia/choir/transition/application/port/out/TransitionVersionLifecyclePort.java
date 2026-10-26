package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.domain.TransitionVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransitionVersionLifecyclePort {

    Flux<TransitionVersion> list(long choirId, long transitionId);

    Mono<TransitionVersion> createDraft(
            long choirId,
            long transitionId,
            long fromFormationVersionId,
            long toFormationVersionId,
            String instructions);
}
