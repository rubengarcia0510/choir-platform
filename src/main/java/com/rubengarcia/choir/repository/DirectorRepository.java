package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Director;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface DirectorRepository
        extends ReactiveCrudRepository<Director, Long> {

    Mono<Director> findByEmailIgnoreCase(String email);
}
