package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Concert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConcertManagementService {

    Flux<Concert> list(String email, long choirId);

    Mono<Concert> get(String email, long choirId, long concertId);

    Mono<Concert> create(
            String email, long choirId, String title,
            java.time.LocalDate concertDate, String venue);

    Mono<Concert> update(
            String email, long choirId, long concertId,
            String title, java.time.LocalDate concertDate, String venue,
            String status);

    Mono<Void> archive(String email, long choirId, long concertId);
}
