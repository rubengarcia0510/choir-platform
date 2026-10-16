package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Formation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface FormationRepository extends ReactiveCrudRepository<Formation, Long> {
    Flux<Formation> findByChoirId(Long choirId);
}
