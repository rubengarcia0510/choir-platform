package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChoirService {

    Flux<Choir> list(String email, boolean includeArchived);

    Mono<Choir> get(String email, Long choirId);

    Mono<Choir> create(String email, String name, String description);

    Mono<Choir> update(
            String email, Long choirId, String name, String description);

    Mono<Void> archive(String email, Long choirId);
}
