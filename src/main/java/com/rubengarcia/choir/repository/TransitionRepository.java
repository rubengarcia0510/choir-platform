package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Transition;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TransitionRepository extends ReactiveCrudRepository<Transition, Long> {
    Flux<Transition> findByChoirId(Long choirId);
}
