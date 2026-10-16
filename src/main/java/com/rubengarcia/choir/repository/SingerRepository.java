package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Singer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SingerRepository extends ReactiveCrudRepository<Singer, Long> {
    Flux<Singer> findByChoirId(Long choirId);
    Flux<Singer> findByChoirIdAndActiveTrue(Long choirId);
}
