package com.rubengarcia.choir.scene.application.usecase;

import com.rubengarcia.choir.scene.application.exception.StageTemplateArchivedException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateNotFoundException;
import com.rubengarcia.choir.scene.application.port.in.StageTemplateUseCase;
import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.scene.application.port.out.StageTemplatePersistencePort;
import com.rubengarcia.choir.scene.domain.StageTemplate;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/** Gestión de plantillas. Autoriza sobre el coro antes de cualquier acceso a persistencia. */
@Service
public class StageTemplateManagementService implements StageTemplateUseCase {

    private final StageTemplatePersistencePort templates;
    private final StageAuthorizationPort authorization;

    public StageTemplateManagementService(
            StageTemplatePersistencePort templates, StageAuthorizationPort authorization) {
        this.templates = templates;
        this.authorization = authorization;
    }

    @Override
    public Flux<StageTemplate> list(String directorEmail, long choirId) {
        return StagePreconditions.requirePositive(directorEmail, choirId)
                .then(Mono.defer(() -> authorization.requireOwnedChoir(directorEmail, choirId)))
                .thenMany(Flux.defer(() -> templates.findByChoir(choirId)));
    }

    @Override
    public Mono<StageTemplate> get(String directorEmail, long choirId, long stageId) {
        return StagePreconditions.requirePositive(directorEmail, choirId, stageId)
                .then(Mono.defer(() -> authorization.requireOwnedChoir(directorEmail, choirId)))
                .then(Mono.defer(() -> findExisting(choirId, stageId)));
    }

    @Override
    public Mono<StageTemplate> create(String directorEmail, StageTemplate draft) {
        return StagePreconditions.requirePositive(directorEmail, draft.choirId())
                .then(Mono.defer(() -> authorization.requireOwnedChoir(directorEmail, draft.choirId())))
                .then(Mono.defer(() -> {
                    if (draft.id() != null) {
                        return Mono.error(new IllegalArgumentException(
                                "Una plantilla nueva no puede traer identificador"));
                    }
                    return templates.save(draft);
                }));
    }

    @Override
    public Mono<StageTemplate> update(
            String directorEmail, long choirId, long stageId, StageTemplate replacement) {
        return StagePreconditions.requirePositive(directorEmail, choirId, stageId)
                .then(Mono.defer(() -> authorization.requireOwnedChoir(directorEmail, choirId)))
                .then(Mono.defer(() -> {
                    if (replacement.id() == null || replacement.id() != stageId
                            || replacement.choirId() != choirId) {
                        return Mono.error(new IllegalArgumentException(
                                "La plantilla a modificar no corresponde al coro o al escenario indicados"));
                    }
                    return findExisting(choirId, stageId)
                            .flatMap(existing -> {
                                if (existing.archived()) {
                                    return Mono.error(new StageTemplateArchivedException());
                                }
                                return templates.save(replacement);
                            });
                }));
    }

    @Override
    public Mono<StageTemplate> archive(String directorEmail, long choirId, long stageId) {
        return StagePreconditions.requirePositive(directorEmail, choirId, stageId)
                .then(Mono.defer(() -> authorization.requireOwnedChoir(directorEmail, choirId)))
                .then(Mono.defer(() -> findExisting(choirId, stageId)))
                .flatMap(existing -> existing.archived()
                        ? Mono.just(existing)
                        : templates.save(existing.archive()));
    }

    private Mono<StageTemplate> findExisting(long choirId, long stageId) {
        return templates.findById(choirId, stageId)
                .switchIfEmpty(Mono.error(new StageTemplateNotFoundException()));
    }
}
