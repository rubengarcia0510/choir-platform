package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Singer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SingerRepository extends ReactiveCrudRepository<Singer, Long> {

    Flux<Singer> findByChoirId(Long choirId);

    Flux<Singer> findByChoirIdAndActiveTrue(Long choirId);

    Mono<Singer> findByIdAndChoirId(Long id, Long choirId);
}
