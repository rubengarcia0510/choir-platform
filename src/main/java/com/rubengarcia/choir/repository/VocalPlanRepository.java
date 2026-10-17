package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.VocalPlan;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface VocalPlanRepository
        extends ReactiveCrudRepository<VocalPlan, Long> {

    Mono<VocalPlan> findByRepertoireItemId(Long repertoireItemId);

    Mono<VocalPlan> findByRepertoireItemIdAndChoirId(
            Long repertoireItemId, Long choirId);
}
