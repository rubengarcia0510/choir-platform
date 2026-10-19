package com.rubengarcia.choir.service;

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
            List<GroupData> groups) {
    }

    record GroupData(String label, int memberCount) {
    }

    record GroupView(int order, String label, int memberCount) {
    }

    record RuleView(
            long id,
            int order,
            String ruleType,
            String description,
            boolean active,
            List<GroupView> groups) {
    }
}
