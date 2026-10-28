package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.SceneRuleRevision;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface SceneRuleRevisionRepository
        extends ReactiveCrudRepository<SceneRuleRevision, Long> {

    Mono<SceneRuleRevision> findFirstBySceneRuleIdOrderByRevisionNumberDesc(
            Long sceneRuleId);
}
