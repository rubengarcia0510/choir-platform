package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.FormationPosition;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface FormationPositionRepository
        extends ReactiveCrudRepository<FormationPosition, Long> {
    Flux<FormationPosition> findByFormationVersionId(Long formationVersionId);
    Flux<FormationPosition> findBySingerId(Long singerId);
}
