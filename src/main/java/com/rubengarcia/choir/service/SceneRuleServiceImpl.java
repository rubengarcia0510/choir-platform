package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.SceneRule;
import com.rubengarcia.choir.domain.SceneRuleGroup;
import com.rubengarcia.choir.repository.SceneRuleGroupRepository;
import com.rubengarcia.choir.repository.SceneRuleMutationRepository;
import com.rubengarcia.choir.repository.SceneRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SceneRuleServiceImpl implements SceneRuleService {

    private static final Set<String> RULE_TYPES =
            Set.of("MOVEMENT", "EXIT", "CAST_DIVISION");

    private final ResourceAuthorizationService authorizationService;
    private final SceneRuleMutationRepository mutationRepository;
    private final SceneRuleRepository ruleRepository;
    private final SceneRuleGroupRepository groupRepository;

    public SceneRuleServiceImpl(
            ResourceAuthorizationService authorizationService,
            SceneRuleMutationRepository mutationRepository,
            SceneRuleRepository ruleRepository,
            SceneRuleGroupRepository groupRepository) {
        this.authorizationService = authorizationService;
        this.mutationRepository = mutationRepository;
        this.ruleRepository = ruleRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public Flux<RuleView> list(
            String email, long choirId, long concertId, long repertoireItemId) {
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .thenMany(ruleRepository
                        .findByRepertoireItemIdOrderByRuleOrderAsc(repertoireItemId)
                        .concatMap(this::toView));
    }

    @Override
    public Mono<RuleView> get(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId) {
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .then(findRule(repertoireItemId, ruleId))
                .flatMap(this::toView);
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<RuleView> create(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            RuleData data) {
        return validate(data)
                .then(authorizeOccurrence(
                        email, choirId, concertId, repertoireItemId))
                .then(lockRepertoireItem(repertoireItemId))
                .then(ruleRepository
                        .findByRepertoireItemIdOrderByRuleOrderDesc(repertoireItemId)
                        .next()
                        .map(rule -> rule.getRuleOrder() + 1)
                        .defaultIfEmpty(0))
                .flatMap(order -> {
                    if (order < 0) {
                        return Mono.error(conflict("No se puede agregar otra regla por límite de orden"));
                    }
                    SceneRule rule = new SceneRule();
                    rule.setRepertoireItemId(repertoireItemId);
                    rule.setRuleOrder(order);
                    rule.setRuleType(normalizeType(data.ruleType()));
                    rule.setDescription(data.description().trim());
                    rule.setActive(true);
                    return ruleRepository.save(rule)
                            .flatMap(saved -> replaceGroups(saved.getId(), data.groups())
                                    .then(toView(saved)));
                });
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<RuleView> update(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId,
            RuleData data) {
        return validate(data)
                .then(authorizeOccurrence(
                        email, choirId, concertId, repertoireItemId))
                .then(lockRepertoireItem(repertoireItemId))
                .then(findRule(repertoireItemId, ruleId))
                .flatMap(rule -> {
                    rule.setRuleType(normalizeType(data.ruleType()));
                    rule.setDescription(data.description().trim());
                    return ruleRepository.save(rule)
                            .flatMap(saved -> replaceGroups(ruleId, data.groups())
                                    .then(toView(saved)));
                });
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> reorder(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(ruleIds).size() != ruleIds.size()) {
            return Mono.error(badRequest("El orden debe incluir IDs de regla únicos y no nulos"));
        }
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .then(lockRepertoireItem(repertoireItemId))
                .then(ruleRepository
                        .findByRepertoireItemIdOrderByRuleOrderDesc(repertoireItemId)
                        .collectList())
                .flatMap(existing -> reorderLocked(ruleIds, existing));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> deactivate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId) {
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .then(lockRepertoireItem(repertoireItemId))
                .then(findRule(repertoireItemId, ruleId))
                .flatMap(rule -> {
                    if (!rule.isActive()) {
                        return Mono.empty();
                    }
                    rule.setActive(false);
                    return ruleRepository.save(rule).then();
                });
    }

    private Mono<Void> reorderLocked(
            List<Long> requestedIds,
            List<SceneRule> existing) {
        Set<Long> existingIds = existing.stream()
                .map(SceneRule::getId)
                .collect(Collectors.toSet());
        if (requestedIds.size() != existing.size()
                || !existingIds.equals(new HashSet<>(requestedIds))) {
            return Mono.error(badRequest(
                    "El orden debe incluir exactamente todas las reglas de la canción"));
        }
        if (existing.isEmpty()) {
            return Mono.empty();
        }

        long maximumOrder = existing.get(0).getRuleOrder();
        long offset = maximumOrder + 1;
        if (maximumOrder + offset > Integer.MAX_VALUE) {
            return Mono.error(conflict("No se puede reordenar por límite de orden"));
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

        return stageCurrentOrders.then(assignRequestedOrders);
    }

    private Mono<Void> authorizeOccurrence(
            String email, long choirId, long concertId, long repertoireItemId) {
        return authorizationService.requireOwnedConcertWork(
                email, choirId, concertId, repertoireItemId);
    }

    private Mono<Void> lockRepertoireItem(long repertoireItemId) {
        return mutationRepository.lockRepertoireItem(repertoireItemId)
                .flatMap(locked -> locked
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Obra de repertorio inexistente")));
    }

    private Mono<SceneRule> findRule(long repertoireItemId, long ruleId) {
        return ruleRepository.findByIdAndRepertoireItemId(ruleId, repertoireItemId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Regla inexistente para esta canción")));
    }

    private Mono<Void> replaceGroups(long ruleId, List<GroupData> groups) {
        return groupRepository.deleteBySceneRuleId(ruleId)
                .thenMany(Flux.range(0, groups.size())
                        .concatMap(order -> {
                            GroupData data = groups.get(order);
                            SceneRuleGroup group = new SceneRuleGroup();
                            group.setSceneRuleId(ruleId);
                            group.setGroupOrder(order);
                            group.setGroupLabel(data.label().trim());
                            group.setMemberCount(data.memberCount());
                            return groupRepository.save(group);
                        }))
                .then();
    }

    private Mono<RuleView> toView(SceneRule rule) {
        return groupRepository.findBySceneRuleIdOrderByGroupOrderAsc(rule.getId())
                .map(group -> new GroupView(
                        group.getGroupOrder(),
                        group.getGroupLabel(),
                        group.getMemberCount()))
                .collectList()
                .map(groups -> new RuleView(
                        rule.getId(),
                        rule.getRuleOrder(),
                        rule.getRuleType(),
                        rule.getDescription(),
                        rule.isActive(),
                        groups));
    }

    private Mono<Void> validate(RuleData data) {
        if (data == null || data.ruleType() == null
                || data.description() == null || data.groups() == null) {
            return Mono.error(badRequest(
                    "Tipo, descripción y grupos de la regla son obligatorios"));
        }
        if (!RULE_TYPES.contains(normalizeType(data.ruleType()))) {
            return Mono.error(badRequest("El tipo de regla no está permitido"));
        }
        if (data.description().isBlank() || data.description().length() > 1000) {
            return Mono.error(badRequest(
                    "La descripción debe tener entre 1 y 1000 caracteres"));
        }
        if (data.groups().stream().anyMatch(group -> group == null
                || group.label() == null || group.label().isBlank()
                || group.label().length() > 100 || group.memberCount() <= 0)) {
            return Mono.error(badRequest(
                    "Cada grupo requiere una etiqueta y una cantidad positiva de integrantes"));
        }
        if ("CAST_DIVISION".equals(normalizeType(data.ruleType()))
                && data.groups().size() < 2) {
            return Mono.error(badRequest(
                    "Una regla de división del elenco requiere al menos dos grupos"));
        }
        return Mono.empty();
    }

    private String normalizeType(String ruleType) {
        return ruleType.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
