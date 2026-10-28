package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.SceneRuleGroup;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SceneRuleGroupRepository
        extends ReactiveCrudRepository<SceneRuleGroup, Long> {

    Flux<SceneRuleGroup> findBySceneRuleIdOrderByGroupOrderAsc(
            Long sceneRuleId);

    Mono<Void> deleteBySceneRuleId(Long sceneRuleId);
}
