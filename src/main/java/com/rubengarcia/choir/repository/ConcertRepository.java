package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Concert;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ConcertRepository extends ReactiveCrudRepository<Concert, Long> {
    Flux<Concert> findByChoirId(Long choirId);
}
