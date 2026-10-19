package com.rubengarcia.choir.service;

import reactor.core.publisher.Mono;

import java.util.List;

public interface ConcertCastSelectionService {

    Mono<Void> replaceSelection(
            String email,
            long choirId,
            long concertId,
            List<Long> singerIds);
}
