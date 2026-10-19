package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Singer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SingerService {

    Flux<Singer> list(String email, Long choirId, boolean includeInactive);

    Mono<Singer> get(String email, Long choirId, Long singerId);

    Mono<Singer> create(
            String email, Long choirId, SingerData data);

    Mono<Singer> update(
            String email, Long choirId, Long singerId, SingerData data);

    Mono<Void> deactivate(String email, Long choirId, Long singerId);

    Mono<Singer> reactivate(String email, Long choirId, Long singerId);

    record SingerData(
            String displayName,
            String email,
            String voicePart,
            Long voiceSectionId,
            String mobility,
            String restrictions) {
    }
}
