package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.ConcertCastMember;
import com.rubengarcia.choir.domain.Singer;
import com.rubengarcia.choir.repository.ConcertCastSelectionRepository;
import com.rubengarcia.choir.repository.SingerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConcertCastSelectionServiceImpl
        implements ConcertCastSelectionService {

    private final ResourceAuthorizationService authorizationService;
    private final SingerRepository singerRepository;
    private final ConcertCastSelectionRepository selectionRepository;

    public ConcertCastSelectionServiceImpl(
            ResourceAuthorizationService authorizationService,
            SingerRepository singerRepository,
            ConcertCastSelectionRepository selectionRepository) {
        this.authorizationService = authorizationService;
        this.singerRepository = singerRepository;
        this.selectionRepository = selectionRepository;
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> replaceSelection(
            String email,
            long choirId,
            long concertId,
            List<Long> singerIds) {

        if (singerIds == null || singerIds.stream().anyMatch(id -> id == null)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La lista de cantantes no puede ser nula ni contener IDs nulos"));
        }

        if (new HashSet<>(singerIds).size() != singerIds.size()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La selección contiene IDs de cantantes duplicados"));
        }

        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .then(singerRepository.findByChoirIdAndActiveTrue(choirId).collectList())
                .flatMap(activeSingers -> {
                    Set<Long> activeIds = activeSingers.stream()
                            .map(Singer::getId)
                            .collect(java.util.stream.Collectors.toSet());

                    if (!activeIds.containsAll(singerIds)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Todos los cantantes seleccionados deben estar activos y pertenecer al coro"));
                    }

                    return selectionRepository.replaceSelection(
                            choirId, concertId, List.copyOf(singerIds));
                });
    }

    @Override
    public Flux<ConcertCastMember> getCast(
            String email, long choirId, long concertId) {
        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .thenMany(selectionRepository.findCast(concertId));
    }
}
