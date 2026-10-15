package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.ConcertCastSnapshot;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ConcertCastSnapshotRepository
        extends ReactiveCrudRepository<ConcertCastSnapshot, Long> {
    Flux<ConcertCastSnapshot> findByConcertId(Long concertId);
}
