package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.RepertoireItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface RepertoireItemRepository
        extends ReactiveCrudRepository<RepertoireItem, Long> {
    Flux<RepertoireItem> findByChoirId(Long choirId);
}
