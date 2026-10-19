package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.FormationVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FormationVersionLifecycleService {

    Flux<FormationVersion> list(
            String email, long choirId, long formationId);

    Mono<FormationVersion> createDraft(
            String email, long choirId, long formationId, String notes);

    Mono<FormationVersion> published(
            String email, long choirId, long formationId);

    Mono<FormationVersion> publish(
            String email, long choirId, long formationId, long versionId);
}
