package com.rubengarcia.choir.formation.adapter.out.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationPositionRepository
        extends ReactiveCrudRepository<FormationPositionEntity, Long> {

    Flux<FormationPositionEntity> findByFormationVersionId(Long formationVersionId);

    Flux<FormationPositionEntity> findBySingerId(Long singerId);

    Mono<Void> deleteByFormationVersionId(Long formationVersionId);
}
