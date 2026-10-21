package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.domain.FormationPosition;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationPositionRepository
        extends ReactiveCrudRepository<FormationPosition, Long> {

    Flux<FormationPosition> findByFormationVersionId(Long formationVersionId);

    Flux<FormationPosition> findBySingerId(Long singerId);

    Mono<Void> deleteByFormationVersionId(Long formationVersionId);
}
