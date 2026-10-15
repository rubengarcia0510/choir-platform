package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.TransitionVersion;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransitionVersionRepository
        extends ReactiveCrudRepository<TransitionVersion, Long> {
    Flux<TransitionVersion> findByTransitionId(Long transitionId);
    Mono<TransitionVersion> findByTransitionIdAndVersionNumber(Long transitionId, int versionNumber);
}
