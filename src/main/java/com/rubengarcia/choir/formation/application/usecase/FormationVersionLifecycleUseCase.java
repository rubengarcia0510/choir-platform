package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.domain.FormationVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionLifecycleUseCase {

    Flux<FormationVersion> list(
            String email,
            long choirId,
            long formationId);

    Mono<FormationVersion> createDraft(
            String email,
            long choirId,
            long formationId,
            String notes);

    Mono<FormationVersion> published(
            String email,
            long choirId,
            long formationId);

    Mono<FormationVersion> publish(
            String email,
            long choirId,
            long formationId,
            long versionId);
}
