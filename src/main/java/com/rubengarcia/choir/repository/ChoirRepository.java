package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Choir;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChoirRepository extends ReactiveCrudRepository<Choir, Long> {
    Flux<Choir> findByDirectorId(Long directorId);

    Flux<Choir> findByDirectorIdAndArchivedFalse(Long directorId);

    Mono<Choir> findByIdAndDirectorId(Long id, Long directorId);
}
