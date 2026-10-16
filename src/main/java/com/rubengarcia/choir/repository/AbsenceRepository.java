package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Absence;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AbsenceRepository extends ReactiveCrudRepository<Absence, Long> {
    Flux<Absence> findBySingerId(Long singerId);
    Flux<Absence> findByConcertId(Long concertId);
}
