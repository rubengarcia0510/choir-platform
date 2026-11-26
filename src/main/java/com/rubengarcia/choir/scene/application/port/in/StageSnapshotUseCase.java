package com.rubengarcia.choir.scene.application.port.in;

import com.rubengarcia.choir.scene.domain.StageSnapshot;
import reactor.core.publisher.Mono;

public interface StageSnapshotUseCase {

    Mono<StageSnapshot> associate(
            String directorEmail, long choirId, long concertId, long repertoireItemId, long stageId);

    Mono<StageSnapshot> findSnapshot(
            String directorEmail, long choirId, long concertId, long repertoireItemId);
}
