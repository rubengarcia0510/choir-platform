package com.rubengarcia.choir.scene.application.usecase;

import com.rubengarcia.choir.scene.application.exception.StageSnapshotAlreadyAssociatedException;
import com.rubengarcia.choir.scene.application.exception.StageSnapshotNotFoundException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateArchivedException;
import com.rubengarcia.choir.scene.application.exception.StageTemplateNotFoundException;
import com.rubengarcia.choir.scene.application.port.in.StageSnapshotUseCase;
import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.scene.application.port.out.StageSnapshotPersistencePort;
import com.rubengarcia.choir.scene.application.port.out.StageTemplatePersistencePort;
import com.rubengarcia.choir.scene.domain.StageSnapshot;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Asociación de un escenario a una inclusión del repertorio. La autorización sobre la ocurrencia se
 * ejecuta antes de leer la plantilla o escribir el snapshot. Una inclusión admite un único snapshot.
 */
@Service
public class StageSnapshotService implements StageSnapshotUseCase {

    private final StageTemplatePersistencePort templates;
    private final StageSnapshotPersistencePort snapshots;
    private final StageAuthorizationPort authorization;

    public StageSnapshotService(
            StageTemplatePersistencePort templates,
            StageSnapshotPersistencePort snapshots,
            StageAuthorizationPort authorization) {
        this.templates = templates;
        this.snapshots = snapshots;
        this.authorization = authorization;
    }

    @Override
    public Mono<StageSnapshot> associate(
            String directorEmail, long choirId, long concertId, long repertoireItemId, long stageId) {
        return StagePreconditions.requirePositive(directorEmail, choirId, concertId, repertoireItemId, stageId)
                .then(Mono.defer(() -> authorization.requireOwnedOccurrence(directorEmail, choirId, concertId, repertoireItemId)))
                .then(Mono.defer(() -> templates.findById(choirId, stageId)
                        .switchIfEmpty(Mono.error(new StageTemplateNotFoundException()))))
                .flatMap(template -> {
                    if (template.archived()) {
                        return Mono.<StageSnapshot>error(new StageTemplateArchivedException());
                    }
                    return snapshots.findForInclusion(choirId, concertId, repertoireItemId)
                            .flatMap(existing -> Mono.<StageSnapshot>error(
                                    new StageSnapshotAlreadyAssociatedException()))
                            .switchIfEmpty(Mono.defer(() -> snapshots.insert(StageSnapshot.of(
                                    template, concertId, repertoireItemId, LocalDateTime.now()))));
                });
    }

    @Override
    public Mono<StageSnapshot> findSnapshot(
            String directorEmail, long choirId, long concertId, long repertoireItemId) {
        return StagePreconditions.requirePositive(directorEmail, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> authorization.requireOwnedOccurrence(directorEmail, choirId, concertId, repertoireItemId)))
                .then(Mono.defer(() -> snapshots.findForInclusion(choirId, concertId, repertoireItemId)))
                .switchIfEmpty(Mono.error(new StageSnapshotNotFoundException()));
    }
}
