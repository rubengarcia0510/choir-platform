package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.domain.SceneRule;
import com.rubengarcia.choir.domain.SceneRuleGroup;
import com.rubengarcia.choir.domain.SceneRuleRevision;
import com.rubengarcia.choir.repository.SceneRuleGroupRepository;
import com.rubengarcia.choir.repository.SceneRuleMutationRepository;
import com.rubengarcia.choir.repository.SceneRuleRepository;
import com.rubengarcia.choir.repository.SceneRuleRevisionRepository;
import com.rubengarcia.choir.repository.SingerRepository;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SceneRulePersistenceAdapter implements SceneRulePersistencePort {

    private static final String CREATE = "CREATE";
    private static final String UPDATE = "UPDATE";
    private static final String REORDER = "REORDER";
    private static final String DEACTIVATE = "DEACTIVATE";

    private final SceneRuleMutationRepository mutationRepository;
    private final SceneRuleRepository ruleRepository;
    private final SceneRuleGroupRepository groupRepository;
    private final SceneRuleRevisionRepository revisionRepository;
    private final SingerRepository singerRepository;
    private final DatabaseClient databaseClient;

    public SceneRulePersistenceAdapter(
            SceneRuleMutationRepository mutationRepository,
            SceneRuleRepository ruleRepository,
            SceneRuleGroupRepository groupRepository,
            SceneRuleRevisionRepository revisionRepository,
            SingerRepository singerRepository,
            DatabaseClient databaseClient) {
        this.mutationRepository = mutationRepository;
        this.ruleRepository = ruleRepository;
        this.groupRepository = groupRepository;
        this.revisionRepository = revisionRepository;
        this.singerRepository = singerRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<SceneRule> findByRepertoireItemId(long repertoireItemId) {
        return ruleRepository.findByRepertoireItemIdOrderByRuleOrderAsc(
                repertoireItemId);
    }

    @Override
    public Mono<SceneRule> findByIdAndRepertoireItemId(
            long ruleId, long repertoireItemId) {
        return ruleRepository.findByIdAndRepertoireItemId(ruleId, repertoireItemId);
    }

    @Override
    public Flux<SceneRuleGroup> findGroupsBySceneRuleId(long ruleId) {
        return groupRepository.findBySceneRuleIdOrderByGroupOrderAsc(ruleId);
    }

    @Override
    public Flux<Long> findSingerIdsByGroupId(long groupId) {
        return databaseClient.sql("""
                SELECT singer_id
                FROM scene_rule_group_members
                WHERE scene_rule_group_id = :groupId
                ORDER BY member_order
                """)
                .bind("groupId", groupId)
                .map((row, metadata) -> row.get("singer_id", Long.class))
                .all();
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<SceneRule> create(
            long choirId, long repertoireItemId, RuleDefinition definition) {
        return validateGroupMembers(choirId, definition.groups())
                .then(lockRepertoireItem(repertoireItemId))
                .then(ruleRepository
                        .findByRepertoireItemIdOrderByRuleOrderDesc(repertoireItemId)
                        .next()
                        .map(rule -> rule.getRuleOrder() + 1)
                        .defaultIfEmpty(0))
                .flatMap(order -> {
                    if (order < 0) {
                        return Mono.error(orderLimitFailure());
                    }
                    SceneRule rule = new SceneRule();
                    rule.setRepertoireItemId(repertoireItemId);
                    rule.setRuleOrder(order);
                    rule.setRuleType(definition.ruleType());
                    rule.setDescription(definition.description());
                    rule.setActive(true);
                    return ruleRepository.save(rule)
                            .flatMap(saved -> replaceGroups(
                                            saved.getId(), definition.groups())
                                    .then(createRevision(saved, CREATE))
                                    .thenReturn(saved));
                });
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<SceneRule> update(
            long choirId,
            long repertoireItemId,
            long ruleId,
            RuleDefinition definition) {
        return validateGroupMembers(choirId, definition.groups())
                .then(lockRepertoireItem(repertoireItemId))
                .then(findRule(repertoireItemId, ruleId))
                .flatMap(rule -> {
                    rule.setRuleType(definition.ruleType());
                    rule.setDescription(definition.description());
                    return ruleRepository.save(rule)
                            .flatMap(saved -> replaceGroups(
                                            ruleId, definition.groups())
                                    .then(createRevision(saved, UPDATE))
                                    .thenReturn(saved));
                });
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> reorder(long repertoireItemId, List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(ruleIds).size() != ruleIds.size()) {
            return Mono.error(invalidOrderFailure(
                    "El orden debe incluir IDs de regla únicos y no nulos"));
        }
        return lockRepertoireItem(repertoireItemId)
                .then(ruleRepository
                        .findByRepertoireItemIdOrderByRuleOrderDesc(repertoireItemId)
                        .collectList())
                .flatMap(existing -> reorderLocked(ruleIds, existing));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> deactivate(long repertoireItemId, long ruleId) {
        return lockRepertoireItem(repertoireItemId)
                .then(findRule(repertoireItemId, ruleId))
                .flatMap(rule -> {
                    if (!rule.isActive()) {
                        return Mono.empty();
                    }
                    rule.setActive(false);
                    return ruleRepository.save(rule)
                            .flatMap(saved -> createRevision(saved, DEACTIVATE))
                            .then();
                });
    }

    private Mono<Void> reorderLocked(
            List<Long> requestedIds, List<SceneRule> existing) {
        var existingIds = existing.stream()
                .map(SceneRule::getId)
                .collect(Collectors.toSet());
        if (requestedIds.size() != existing.size()
                || !existingIds.equals(new HashSet<>(requestedIds))) {
            return Mono.error(invalidOrderFailure(
                    "El orden debe incluir exactamente todas las reglas de la canción"));
        }
        if (existing.isEmpty()) {
            return Mono.empty();
        }

        long maximumOrder = existing.get(0).getRuleOrder();
        long offset = maximumOrder + 1;
        if (maximumOrder + offset > Integer.MAX_VALUE) {
            return Mono.error(orderLimitFailure());
        }

        Map<Long, SceneRule> rulesById = existing.stream()
                .collect(Collectors.toMap(SceneRule::getId, Function.identity()));
        Mono<Void> stageCurrentOrders = Flux.fromIterable(existing)
                .concatMap(rule -> {
                    rule.setRuleOrder((int) (rule.getRuleOrder() + offset));
                    return ruleRepository.save(rule);
                })
                .then();
        Mono<Void> assignRequestedOrders = Flux.range(0, requestedIds.size())
                .concatMap(order -> {
                    SceneRule rule = rulesById.get(requestedIds.get(order));
                    rule.setRuleOrder(order);
                    return ruleRepository.save(rule);
                })
                .then();
        Mono<Void> snapshotReorderedRules = Flux.fromIterable(requestedIds)
                .concatMap(id -> createRevision(rulesById.get(id), REORDER))
                .then();

        return stageCurrentOrders
                .then(assignRequestedOrders)
                .then(snapshotReorderedRules);
    }

    private Mono<Void> createRevision(SceneRule rule, String revisionKind) {
        return revisionRepository
                .findFirstBySceneRuleIdOrderByRevisionNumberDesc(rule.getId())
                .map(SceneRuleRevision::getRevisionNumber)
                .defaultIfEmpty(0)
                .flatMap(lastRevision -> {
                    SceneRuleRevision revision = new SceneRuleRevision();
                    revision.setSceneRuleId(rule.getId());
                    revision.setRevisionNumber(lastRevision + 1);
                    revision.setRepertoireItemId(rule.getRepertoireItemId());
                    revision.setRuleOrder(rule.getRuleOrder());
                    revision.setRuleType(rule.getRuleType());
                    revision.setDescription(rule.getDescription());
                    revision.setActive(rule.isActive());
                    revision.setRevisionKind(revisionKind);
                    return revisionRepository.save(revision);
                })
                .flatMap(savedRevision -> findGroupsBySceneRuleId(rule.getId())
                        .concatMap(group -> saveRevisionGroup(
                                savedRevision.getId(), group))
                        .then());
    }

    private Mono<Void> saveRevisionGroup(long revisionId, SceneRuleGroup group) {
        return databaseClient.sql("""
                INSERT INTO scene_rule_revision_groups (
                    revision_id, group_order, group_label, member_count,
                    composition_mode
                ) VALUES (
                    :revisionId, :groupOrder, :groupLabel, :memberCount,
                    :compositionMode
                )
                """)
                .bind("revisionId", revisionId)
                .bind("groupOrder", group.getGroupOrder())
                .bind("groupLabel", group.getGroupLabel())
                .bind("memberCount", group.getMemberCount())
                .bind("compositionMode", group.getCompositionMode())
                .fetch()
                .rowsUpdated()
                .then(findSingerIdsByGroupId(group.getId())
                        .index()
                        .concatMap(member -> saveRevisionGroupMember(
                                revisionId,
                                group.getGroupOrder(),
                                member.getT2(),
                                Math.toIntExact(member.getT1())))
                        .then());
    }

    private Mono<Void> replaceGroups(
            long ruleId, List<GroupDefinition> groups) {
        return groupRepository.deleteBySceneRuleId(ruleId)
                .thenMany(Flux.range(0, groups.size())
                        .concatMap(order -> {
                            GroupDefinition definition = groups.get(order);
                            SceneRuleGroup group = new SceneRuleGroup();
                            group.setSceneRuleId(ruleId);
                            group.setGroupOrder(order);
                            group.setGroupLabel(definition.label());
                            group.setMemberCount(definition.memberCount());
                            group.setCompositionMode(definition.compositionMode());
                            return groupRepository.save(group);
                        }))
                .concatMap(savedGroup -> {
                    GroupDefinition definition =
                            groups.get(savedGroup.getGroupOrder());
                    return Flux.fromIterable(definition.singerIds())
                            .index()
                            .concatMap(member -> saveGroupMember(
                                    savedGroup.getId(),
                                    member.getT2(),
                                    Math.toIntExact(member.getT1())))
                            .then();
                })
                .then();
    }

    private Mono<Void> validateGroupMembers(
            long choirId, List<GroupDefinition> groups) {
        return Flux.fromIterable(groups)
                .concatMap(group -> Flux.fromIterable(group.singerIds()))
                .distinct()
                .concatMap(singerId -> singerRepository
                        .findByIdAndChoirId(singerId, choirId)
                        .switchIfEmpty(Mono.error(new Failure(
                                FailureType.INVALID_GROUP_MEMBERS,
                                "Los coreutas de un grupo deben pertenecer al coro"))))
                .then();
    }

    private Mono<Void> saveGroupMember(
            long groupId, long singerId, int memberOrder) {
        return databaseClient.sql("""
                INSERT INTO scene_rule_group_members (
                    scene_rule_group_id, singer_id, member_order
                ) VALUES (
                    :groupId, :singerId, :memberOrder
                )
                """)
                .bind("groupId", groupId)
                .bind("singerId", singerId)
                .bind("memberOrder", memberOrder)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> saveRevisionGroupMember(
            long revisionId,
            int groupOrder,
            long singerId,
            int memberOrder) {
        return databaseClient.sql("""
                INSERT INTO scene_rule_revision_group_members (
                    revision_id, group_order, singer_id, member_order
                ) VALUES (
                    :revisionId, :groupOrder, :singerId, :memberOrder
                )
                """)
                .bind("revisionId", revisionId)
                .bind("groupOrder", groupOrder)
                .bind("singerId", singerId)
                .bind("memberOrder", memberOrder)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> lockRepertoireItem(long repertoireItemId) {
        return mutationRepository.lockRepertoireItem(repertoireItemId)
                .flatMap(locked -> locked
                        ? Mono.empty()
                        : Mono.error(new Failure(
                                FailureType.NOT_FOUND,
                                "Obra de repertorio inexistente")));
    }

    private Mono<SceneRule> findRule(long repertoireItemId, long ruleId) {
        return findByIdAndRepertoireItemId(ruleId, repertoireItemId)
                .switchIfEmpty(Mono.error(new Failure(
                        FailureType.NOT_FOUND,
                        "Regla inexistente para esta canción")));
    }

    private Failure invalidOrderFailure(String message) {
        return new Failure(FailureType.INVALID_ORDER, message);
    }

    private Failure orderLimitFailure() {
        return new Failure(
                FailureType.ORDER_LIMIT,
                "No se puede agregar o reordenar reglas por límite de orden");
    }
}
