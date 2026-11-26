package com.rubengarcia.choir.scene.application.port.out;

import com.rubengarcia.choir.scene.domain.StageTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StageTemplatePersistencePort {

    Flux<StageTemplate> findByChoir(long choirId);

    Mono<StageTemplate> findById(long choirId, long stageId);

    /** Inserta si la plantilla no tiene id; actualiza en otro caso. */
    Mono<StageTemplate> save(StageTemplate template);
}
