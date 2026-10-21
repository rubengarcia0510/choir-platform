package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.domain.FormationVersion;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionRepository
        extends ReactiveCrudRepository<FormationVersion, Long> {
    Flux<FormationVersion> findByFormationId(Long formationId);
    Mono<FormationVersion> findByFormationIdAndVersionNumber(Long formationId, int versionNumber);
}
