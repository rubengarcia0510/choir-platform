package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.domain.Formation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface FormationRepository extends ReactiveCrudRepository<Formation, Long> {
    Flux<Formation> findByChoirId(Long choirId);
}
