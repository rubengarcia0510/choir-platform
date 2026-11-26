package com.rubengarcia.choir.scene.application.port.out;

import com.rubengarcia.choir.scene.domain.StageSnapshot;
import reactor.core.publisher.Mono;

/**
 * Solo inserción y lectura. No hay operaciones de actualización ni de borrado: los snapshots son
 * históricos e inmutables por diseño del contrato.
 */
public interface StageSnapshotPersistencePort {

    Mono<StageSnapshot> findForInclusion(long choirId, long concertId, long repertoireItemId);

    Mono<StageSnapshot> insert(StageSnapshot snapshot);
}
