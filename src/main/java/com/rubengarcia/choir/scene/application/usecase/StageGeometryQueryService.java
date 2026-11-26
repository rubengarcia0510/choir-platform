package com.rubengarcia.choir.scene.application.usecase;

import com.rubengarcia.choir.scene.application.exception.StageSnapshotNotFoundException;
import com.rubengarcia.choir.scene.application.model.StageGeometry;
import com.rubengarcia.choir.scene.application.port.in.StageGeometryQueryUseCase;
import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.scene.application.port.out.StageSnapshotPersistencePort;
import reactor.core.publisher.Mono;

/** Consulta de la geometría histórica de una inclusión, sin acceder a tablas de escena desde fuera. */
public class StageGeometryQueryService implements StageGeometryQueryUseCase {

    private final StageSnapshotPersistencePort snapshots;
    private final StageAuthorizationPort authorization;

    public StageGeometryQueryService(
            StageSnapshotPersistencePort snapshots, StageAuthorizationPort authorization) {
        this.snapshots = snapshots;
        this.authorization = authorization;
    }

    @Override
    public Mono<StageGeometry> geometry(
            String directorEmail, long choirId, long concertId, long repertoireItemId) {
        return StagePreconditions.requirePositive(directorEmail, choirId, concertId, repertoireItemId)
                .then(Mono.defer(() -> authorization.requireOwnedOccurrence(directorEmail, choirId, concertId, repertoireItemId)))
                .then(Mono.defer(() -> snapshots.findForInclusion(choirId, concertId, repertoireItemId)))
                .switchIfEmpty(Mono.error(new StageSnapshotNotFoundException()))
                .map(StageGeometry::from);
    }
}
