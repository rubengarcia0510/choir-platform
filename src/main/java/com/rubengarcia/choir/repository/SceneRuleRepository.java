package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.SceneRule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SceneRuleRepository
        extends ReactiveCrudRepository<SceneRule, Long> {

    Flux<SceneRule> findByRepertoireItemIdOrderByRuleOrderAsc(
            Long repertoireItemId);

    Flux<SceneRule> findByRepertoireItemIdAndActiveTrueOrderByRuleOrderAsc(
            Long repertoireItemId);
}
