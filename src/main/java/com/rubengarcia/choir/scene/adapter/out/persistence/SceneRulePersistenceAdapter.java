package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.domain.SceneRule;
import com.rubengarcia.choir.domain.SceneRuleGroup;
import com.rubengarcia.choir.domain.SceneRuleRevision;
import com.rubengarcia.choir.repository.SceneRuleGroupRepository;
import com.rubengarcia.choir.repository.SceneRuleMutationRepository;
import com.rubengarcia.choir.repository.SceneRuleRepository;
import com.rubengarcia.choir.repository.SceneRuleRevisionRepository;
import com.rubengarcia.choir.repository.SingerRepository;
import com.rubengarcia.choir.scene.application.port.out.SceneRuleHistoryPort;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort;
import com.rubengarcia.choir.scene.domain.SceneRuleCondition;
import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SceneRulePersistenceAdapter
        implements SceneRulePersistencePort, SceneRuleHistoryPort {

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
    private final ObjectMapper objectMapper;

    public SceneRulePersistenceAdapter(
            SceneRuleMutationRepository mutationRepository,
            SceneRuleRepository ruleRepository,
            SceneRuleGroupRepository groupRepository,
            SceneRuleRevisionRepository revisionRepository,
            SingerRepository singerRepository,
            DatabaseClient databaseClient,
            ObjectMapper objectMapper) {
        this.mutationRepository = mutationRepository;
        this.ruleRepository = ruleRepository;
        this.groupRepository = groupRepository;
        this.revisionRepository = revisionRepository;
        this.singerRepository = singerRepository;
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    private SceneRule hydrateCondition(SceneRule rule) {
        rule.setCondition(readCondition(rule.getConditionJson()));
        return rule;
    }

    private String writeCondition(SceneRuleCondition condition) {
        if (condition == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(condition);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar la condición estructurada de la regla",
                    exception);
        }
    }

    private SceneRuleCondition readCondition(String conditionJson) {
        if (conditionJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(conditionJson, SceneRuleCondition.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "No se pudo leer la condición estructurada de una revisión",
                    exception);
        }
    }

    @Override
    public Flux<SceneRule> findByRepertoireItemId(long repertoireItemId) {
        return ruleRepository.findByRepertoireItemIdOrderByRuleOrderAsc(
                        repertoireItemId)
                .map(this::hydrateCondition);
    }

    @Override
    public Mono<SceneRule> findByIdAndRepertoireItemId(
            long ruleId, long repertoireItemId) {
        return ruleRepository.findByIdAndRepertoireItemId(ruleId, repertoireItemId)
                .map(this::hydrateCondition);
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
    public Flux<SceneRuleRevisionSnapshot> findAllRevisionsByRepertoireItemId(
            long repertoireItemId) {
        return databaseClient.sql("""
                SELECT
                    revision.id AS revision_id,
                    revision.scene_rule_id,
                    revision.revision_number,
                    revision.repertoire_item_id,
                    revision.rule_order,
                    revision.rule_type,
                    revision.description,
                    revision.active,
                    revision.revision_kind,
                    revision.created_at,
                    revision.condition_json,
                    revision.definition_status,
                    revision_group.group_order,
                    revision_group.group_label,
                    revision_group.member_count,
                    revision_group.composition_mode,
                    member.singer_id
                FROM scene_rule_revisions revision
                LEFT JOIN scene_rule_revision_groups revision_group
                  ON revision_group.revision_id = revision.id
                LEFT JOIN scene_rule_revision_group_members member
                  ON member.revision_id = revision_group.revision_id
                 AND member.group_order = revision_group.group_order
                WHERE revision.repertoire_item_id = :repertoireItemId
                ORDER BY
                    revision.scene_rule_id,
                    revision.revision_number,
                    revision_group.group_order,
                    member.member_order
                """)
                .bind("repertoireItemId", repertoireItemId)
                .map((row, metadata) -> new RevisionRow(
                        row.get("revision_id", Long.class),
                        row.get("scene_rule_id", Long.class),
                        row.get("revision_number", Integer.class),
                        row.get("repertoire_item_id", Long.class),
                        row.get("rule_order", Integer.class),
                        row.get("rule_type", String.class),
                        row.get("description", String.class),
                        row.get("active", Boolean.class),
                        row.get("revision_kind", String.class),
                        row.get("created_at", LocalDateTime.class),
                        row.get("condition_json", String.class),
                        row.get("definition_status", String.class),
                        row.get("group_order", Integer.class),
                        row.get("group_label", String.class),
                        row.get("member_count", Integer.class),
                        row.get("composition_mode", String.class),
                        row.get("singer_id", Long.class)))
                .all()
                .collectList()
                .map(this::assembleRevisionSnapshots)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<SceneRule> create(
            long choirId, long repertoireItemId, RuleDefinition definition) {
        return validateGroupMembers(
                        choirId, definition.groups(), definition.condition())
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
                    rule.setCondition(definition.condition());
                    rule.setConditionJson(writeCondition(definition.condition()));
                    rule.setDefinitionStatus(definition.definitionStatus());
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
        return validateGroupMembers(
                        choirId, definition.groups(), definition.condition())
                .then(lockRepertoireItem(repertoireItemId))
                .then(findRule(repertoireItemId, ruleId))
                .flatMap(rule -> {
                    rule.setRuleType(definition.ruleType());
                    rule.setDescription(definition.description());
                    rule.setCondition(definition.condition());
                    rule.setConditionJson(writeCondition(definition.condition()));
                    rule.setDefinitionStatus(definition.definitionStatus());
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
                    revision.setConditionJson(rule.getConditionJson());
                    revision.setDefinitionStatus(rule.getDefinitionStatus());
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
            long choirId,
            List<GroupDefinition> groups,
            SceneRuleCondition condition) {
        List<Long> referencedSingerIds = conditionSingerIds(condition);
        return Flux.concat(
                Flux.fromIterable(groups)
                        .concatMap(group -> Flux.fromIterable(
                                group.singerIds())),
                Flux.fromIterable(referencedSingerIds))
                .distinct()
                .concatMap(singerId -> singerRepository
                        .findByIdAndChoirId(singerId, choirId)
                        .switchIfEmpty(Mono.error(new Failure(
                                FailureType.INVALID_GROUP_MEMBERS,
                                "Los coreutas de un grupo deben pertenecer al coro"))))
                .then();
    }

    private List<Long> conditionSingerIds(SceneRuleCondition condition) {
        if (condition == null) {
            return List.of();
        }
        List<SceneRuleCondition.ParticipantRef> participants = new ArrayList<>();
        if (condition.exitParticipants() != null) {
            participants.addAll(condition.exitParticipants());
        }
        if (condition.spatialConstraints() != null) {
            condition.spatialConstraints().stream()
                    .filter(constraint -> constraint != null)
                    .forEach(constraint -> {
                        if (constraint.participants() != null) {
                            participants.addAll(constraint.participants());
                        }
                        if (constraint.comparedParticipants() != null) {
                            participants.addAll(constraint.comparedParticipants());
                        }
                    });
        }
        return participants.stream()
                .filter(participant -> participant != null
                        && participant.type()
                                == SceneRuleCondition.ParticipantType.SINGER)
                .map(SceneRuleCondition.ParticipantRef::singerId)
                .filter(singerId -> singerId != null)
                .distinct()
                .toList();
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

    private List<SceneRuleRevisionSnapshot> assembleRevisionSnapshots(
            List<RevisionRow> rows) {
        Map<Long, RevisionAccumulator> revisions = new LinkedHashMap<>();
        for (RevisionRow row : rows) {
            RevisionAccumulator revision = revisions.computeIfAbsent(
                    row.revisionId(),
                    ignored -> new RevisionAccumulator(row));
            if (row.groupOrder() != null) {
                GroupAccumulator group = revision.groups().computeIfAbsent(
                        row.groupOrder(),
                        ignored -> new GroupAccumulator(row));
                if (row.singerId() != null) {
                    group.singerIds().add(row.singerId());
                }
            }
        }
        return revisions.values().stream()
                .map(RevisionAccumulator::snapshot)
                .toList();
    }

    private record RevisionRow(
            Long revisionId,
            Long sceneRuleId,
            Integer revisionNumber,
            Long repertoireItemId,
            Integer ruleOrder,
            String ruleType,
            String description,
            Boolean active,
            String revisionKind,
            LocalDateTime createdAt,
            String conditionJson,
            String definitionStatus,
            Integer groupOrder,
            String groupLabel,
            Integer memberCount,
            String compositionMode,
            Long singerId) {
    }

    private final class RevisionAccumulator {

        private final RevisionRow row;
        private final Map<Integer, GroupAccumulator> groups = new LinkedHashMap<>();

        private RevisionAccumulator(RevisionRow row) {
            this.row = row;
        }

        private Map<Integer, GroupAccumulator> groups() {
            return groups;
        }

        private SceneRuleRevisionSnapshot snapshot() {
            return new SceneRuleRevisionSnapshot(
                    row.revisionId(),
                    row.sceneRuleId(),
                    row.revisionNumber(),
                    row.repertoireItemId(),
                    row.ruleOrder(),
                    row.ruleType(),
                    row.description(),
                    row.active(),
                    row.definitionStatus(),
                    row.revisionKind(),
                    row.createdAt(),
                    groups.values().stream()
                            .map(GroupAccumulator::snapshot)
                            .toList(),
                    readCondition(row.conditionJson()));
        }
    }

    private static final class GroupAccumulator {

        private final int groupOrder;
        private final String label;
        private final int memberCount;
        private final String compositionMode;
        private final List<Long> singerIds = new ArrayList<>();

        private GroupAccumulator(RevisionRow row) {
            groupOrder = row.groupOrder();
            label = row.groupLabel();
            memberCount = row.memberCount();
            compositionMode = row.compositionMode();
        }

        private List<Long> singerIds() {
            return singerIds;
        }

        private SceneRuleRevisionSnapshot.GroupSnapshot snapshot() {
            return new SceneRuleRevisionSnapshot.GroupSnapshot(
                    groupOrder, label, memberCount, compositionMode, singerIds);
        }
    }
}
