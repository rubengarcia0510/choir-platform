package com.rubengarcia.choir.formation.application.port.out;

import com.rubengarcia.choir.formation.domain.FormationVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionLifecyclePort {

    Flux<FormationVersion> list(long choirId, long formationId);

    Mono<FormationVersion> createDraft(
            long choirId, long formationId, String notes);

    Mono<FormationVersion> published(long choirId, long formationId);

    Mono<FormationVersion> publish(
            long choirId, long formationId, long versionId);
}
