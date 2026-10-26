package com.rubengarcia.choir.service;

import com.rubengarcia.choir.scene.domain.SceneRuleCondition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SceneRuleService {

    Flux<RuleView> list(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId);

    Mono<RuleView> get(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId);

    Mono<RuleView> create(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            RuleData data);

    Mono<RuleView> update(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId,
            RuleData data);

    Mono<Void> reorder(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            List<Long> ruleIds);

    Mono<Void> deactivate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId);

    record RuleData(
            String ruleType,
            String description,
            List<GroupData> groups,
            SceneRuleCondition condition,
            Boolean draft) {
        public RuleData(
                String ruleType, String description, List<GroupData> groups) {
            this(ruleType, description, groups, null, null);
        }

        public RuleData(
                String ruleType,
                String description,
                List<GroupData> groups,
                SceneRuleCondition condition) {
            this(ruleType, description, groups, condition, null);
        }
    }

    record GroupData(
            String label,
            String compositionMode,
            Integer memberCount,
            List<Long> singerIds) {
        public GroupData(String label, int memberCount) {
            this(label, "COUNT", memberCount, List.of());
        }
    }

    record GroupView(
            int order,
            String label,
            String compositionMode,
            int memberCount,
            List<Long> singerIds) {
    }

    record RuleView(
            long id,
            int order,
            String ruleType,
            String description,
            boolean active,
            List<GroupView> groups,
            SceneRuleCondition condition,
            String definitionStatus) {
    }
}
