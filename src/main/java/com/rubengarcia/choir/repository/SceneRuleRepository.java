package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.SceneRule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SceneRuleRepository
        extends ReactiveCrudRepository<SceneRule, Long> {

    Flux<SceneRule> findByRepertoireItemIdOrderByRuleOrderAsc(
            Long repertoireItemId);

    Flux<SceneRule> findByRepertoireItemIdOrderByRuleOrderDesc(
            Long repertoireItemId);

    Mono<SceneRule> findByIdAndRepertoireItemId(
            Long id,
            Long repertoireItemId);

    Flux<SceneRule> findByRepertoireItemIdAndActiveTrueOrderByRuleOrderAsc(
            Long repertoireItemId);
}
