package com.rubengarcia.choir.scene.application.port.in;

import com.rubengarcia.choir.scene.application.model.StageGeometry;
import reactor.core.publisher.Mono;

/** Contrato de consulta de geometría histórica para CHOIR-25. No expone persistencia. */
public interface StageGeometryQueryUseCase {

    Mono<StageGeometry> geometry(String directorEmail, long choirId, long concertId, long repertoireItemId);
}
