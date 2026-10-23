package com.rubengarcia.choir.scene.application.port.out;

import com.rubengarcia.choir.domain.SceneRule;
import com.rubengarcia.choir.domain.SceneRuleGroup;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SceneRulePersistencePort {

    Flux<SceneRule> findByRepertoireItemId(long repertoireItemId);

    Mono<SceneRule> findByIdAndRepertoireItemId(long ruleId, long repertoireItemId);

    Flux<SceneRuleGroup> findGroupsBySceneRuleId(long ruleId);

    Mono<SceneRule> create(long repertoireItemId, RuleDefinition definition);

    Mono<SceneRule> update(
            long repertoireItemId, long ruleId, RuleDefinition definition);

    Mono<Void> reorder(long repertoireItemId, List<Long> ruleIds);

    Mono<Void> deactivate(long repertoireItemId, long ruleId);

    record RuleDefinition(
            String ruleType,
            String description,
            List<GroupDefinition> groups) {
    }

    record GroupDefinition(String label, int memberCount) {
    }

    enum FailureType {
        NOT_FOUND,
        INVALID_ORDER,
        ORDER_LIMIT
    }

    final class Failure extends RuntimeException {

        private final FailureType type;

        public Failure(FailureType type, String message) {
            super(message);
            this.type = type;
        }

        public FailureType type() {
            return type;
        }
    }
}
