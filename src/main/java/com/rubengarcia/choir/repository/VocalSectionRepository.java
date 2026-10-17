package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.VocalSection;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface VocalSectionRepository
        extends ReactiveCrudRepository<VocalSection, Long> {

    Flux<VocalSection> findByChoirIdOrderBySortOrderAscNameAsc(Long choirId);

    Flux<VocalSection> findByChoirIdAndActiveTrueOrderBySortOrderAscNameAsc(
            Long choirId);
}
