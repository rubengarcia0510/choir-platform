package com.rubengarcia.choir.scene.application.port.in;

import com.rubengarcia.choir.scene.domain.StageTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StageTemplateUseCase {

    Flux<StageTemplate> list(String directorEmail, long choirId);

    Mono<StageTemplate> get(String directorEmail, long choirId, long stageId);

    Mono<StageTemplate> create(String directorEmail, StageTemplate draft);

    Mono<StageTemplate> update(String directorEmail, long choirId, long stageId, StageTemplate replacement);

    Mono<StageTemplate> archive(String directorEmail, long choirId, long stageId);
}
