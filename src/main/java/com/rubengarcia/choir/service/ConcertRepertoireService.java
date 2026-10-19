package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.ConcertRepertoireEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ConcertRepertoireService {

    Flux<ConcertRepertoireEntry> getRepertoire(
            String email, long choirId, long concertId);

    Mono<Void> replaceRepertoire(
            String email,
            long choirId,
            long concertId,
            List<SongSelection> songs);

    record SongSelection(
            long repertoireItemId,
            Integer durationMinutes,
            String stageRequirements) {
    }
}
