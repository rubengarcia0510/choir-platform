package com.rubengarcia.choir.formation.adapter.out.persistence;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface FormationRepository extends ReactiveCrudRepository<FormationEntity, Long> {

    Flux<FormationEntity> findByChoirId(Long choirId);

    Flux<FormationEntity> findByChoirIdAndConcertIdAndRepertoireItemId(
            Long choirId,
            Long concertId,
            Long repertoireItemId);
}
