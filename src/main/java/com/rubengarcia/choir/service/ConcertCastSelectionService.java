package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.ConcertCastMember;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ConcertCastSelectionService {

    Mono<Void> replaceSelection(
            String email,
            long choirId,
            long concertId,
            List<Long> singerIds);

    Flux<ConcertCastMember> getCast(
            String email,
            long choirId,
            long concertId);
}
