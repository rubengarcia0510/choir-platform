package com.rubengarcia.choir.formation.adapter.out.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionRepository
        extends ReactiveCrudRepository<FormationVersionEntity, Long> {
    Flux<FormationVersionEntity> findByFormationId(Long formationId);
    Mono<FormationVersionEntity> findByFormationIdAndVersionNumber(Long formationId, int versionNumber);
}
