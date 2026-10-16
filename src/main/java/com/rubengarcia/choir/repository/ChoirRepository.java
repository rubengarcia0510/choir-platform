package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Choir;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ChoirRepository extends ReactiveCrudRepository<Choir, Long> {
    Flux<Choir> findByDirectorId(Long directorId);
}
