package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.SceneRule;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort.Failure;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort.FailureType;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort.GroupDefinition;
import com.rubengarcia.choir.scene.application.port.out.SceneRulePersistencePort.RuleDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SceneRuleServiceImpl implements SceneRuleService {

    private static final Set<String> RULE_TYPES =
            Set.of("MOVEMENT", "EXIT", "CAST_DIVISION");

    private final ResourceAuthorizationService authorizationService;
    private final SceneRulePersistencePort persistencePort;

    public SceneRuleServiceImpl(
            ResourceAuthorizationService authorizationService,
            SceneRulePersistencePort persistencePort) {
        this.authorizationService = authorizationService;
        this.persistencePort = persistencePort;
    }

    @Override
    public Flux<RuleView> list(
            String email, long choirId, long concertId, long repertoireItemId) {
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .thenMany(persistencePort.findByRepertoireItemId(repertoireItemId)
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
    public Mono<RuleView> create(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            RuleData data) {
        return validate(data)
                .then(authorizeOccurrence(
                        email, choirId, concertId, repertoireItemId))
                .then(Mono.defer(() ->
                        persistencePort.create(repertoireItemId, toDefinition(data))))
                .onErrorMap(Failure.class, this::mapPersistenceFailure)
                .flatMap(this::toView);
    }

    @Override
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
                .then(Mono.defer(() -> persistencePort.update(
                        repertoireItemId, ruleId, toDefinition(data))))
                .onErrorMap(Failure.class, this::mapPersistenceFailure)
                .flatMap(this::toView);
    }

    @Override
    public Mono<Void> reorder(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(ruleIds).size() != ruleIds.size()) {
            return Mono.error(badRequest(
                    "El orden debe incluir IDs de regla únicos y no nulos"));
        }
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> persistencePort.reorder(
                        repertoireItemId, ruleIds)))
                .onErrorMap(Failure.class, this::mapPersistenceFailure);
    }

    @Override
    public Mono<Void> deactivate(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long ruleId) {
        return authorizeOccurrence(email, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> persistencePort.deactivate(
                        repertoireItemId, ruleId)))
                .onErrorMap(Failure.class, this::mapPersistenceFailure);
    }

    private Mono<Void> authorizeOccurrence(
            String email, long choirId, long concertId, long repertoireItemId) {
        return authorizationService.requireOwnedConcertWork(
                email, choirId, concertId, repertoireItemId);
    }

    private Mono<SceneRule> findRule(long repertoireItemId, long ruleId) {
        return persistencePort.findByIdAndRepertoireItemId(ruleId, repertoireItemId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Regla inexistente para esta canción")));
    }

    private Mono<RuleView> toView(SceneRule rule) {
        return persistencePort.findGroupsBySceneRuleId(rule.getId())
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

    private RuleDefinition toDefinition(RuleData data) {
        return new RuleDefinition(
                normalizeType(data.ruleType()),
                data.description().trim(),
                data.groups().stream()
                        .map(group -> new GroupDefinition(
                                group.label().trim(), group.memberCount()))
                        .toList());
    }

    private ResponseStatusException mapPersistenceFailure(Failure failure) {
        return switch (failure.type()) {
            case NOT_FOUND -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, failure.getMessage(), failure);
            case INVALID_ORDER -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, failure.getMessage(), failure);
            case ORDER_LIMIT -> new ResponseStatusException(
                    HttpStatus.CONFLICT, failure.getMessage(), failure);
        };
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
