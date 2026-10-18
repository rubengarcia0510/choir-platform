package com.rubengarcia.choir.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ConcertPublicationService {

    record Publication(
            long concertId,
            String status,
            LocalDateTime publishedAt,
            long castSnapshots,
            long vocalAssignmentSnapshots) {}

    Mono<Publication> publish(String email, long choirId, long concertId);
}
